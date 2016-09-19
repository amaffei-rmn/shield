package shield.swagger

import java.util
import io.swagger.models.parameters._
import io.swagger.models.{Response, Operation, Swagger}
import shield.config.ServiceLocation
import scala.collection.JavaConverters._
import shield.routing._
import scala.concurrent.Future

object SwaggerFetcher {
  def swaggerSpec(endpoints: Iterable[(EndpointTemplate, EndpointDetails)]): Swagger = {
    endpoints.map(_._2.swagger).reduceLeftOption[Swagger]((swag1, swag2) => {
        if(swag2.getTags != null) {
          swag2.getTags.asScala.foreach(tag => swag1.addTag(tag))
        }
        if(swag2.getDefinitions != null) {
          swag2.getDefinitions.asScala.foreach(definition => swag1.addDefinition(definition._1,definition._2))
        }

        val pathList = Option(swag1.getPaths).getOrElse(new util.HashMap[String,io.swagger.models.Path]()).asScala.toList ++ Option(swag2.getPaths).getOrElse(new util.HashMap[String,io.swagger.models.Path]()).asScala.toList
        val operationsByPath = pathList.groupBy(_._1).map { case (uri, path) =>
          val methodMap = path.flatMap(_._2.getOperationMap.asScala.toList).groupBy(_._1).map(i => i._1 -> i._2.map(_._2).reduce[Operation](mergeOperation))
          val p = new io.swagger.models.Path()
          p.setParameters(path.map(_._2.getParameters).filter(p => p != null).map(_.asScala.toList).reduceLeftOption[List[Parameter]](mergeParameters).getOrElse(List[Parameter]()).asJava)
          methodMap.foreach { kvp =>
            p.set(kvp._1.toString().toLowerCase, kvp._2)
          }
          uri -> p
        }
        swag1.paths(operationsByPath.asJava)
      }).getOrElse(new Swagger())
  }

  def mergeOperation(o1: Operation, o2: Operation): Operation = {
    if(o1 == null || o1.equals(o2)) o2
    else if (o2 == null) o1
    else {
      o2.setParameters(mergeParameters(o1.getParameters.asScala.toList,o2.getParameters.asScala.toList).asJava)
      o2.setConsumes((toScalaList[String](o2.getConsumes) ++ toScalaList[String](o1.getConsumes)).distinct.asJava)
      o2.setProduces((toScalaList[String](o2.getProduces) ++ toScalaList[String](o1.getProduces)).distinct.asJava)
      o1.getVendorExtensions.asScala.foreach(ve => o2.setVendorExtension(ve._1, ve._2)) //will overwrite vendor extensions that currently exist in o2 if they also exist in o1
      Option(o1.getResponses).getOrElse(new java.util.HashMap[String, Response]()).asScala.foreach(res => o2.addResponse(res._1, res._2))
      o2
    }
  }

  def mergeParameters(slist1: List[Parameter], slist2: List[Parameter]): List[Parameter] = {
    var mergedParams = List[Parameter]()
    //merge lists and dedupe by name and type of parameter(in)
    val dedupedParams = (slist1 ::: slist2)
      .groupBy(param => (param.getName, param.getIn)).map(_._2.head)
    dedupedParams.foreach(p => {
      //Merge each parameter for a given name while adding it to the list
      val c = slist1.find(p2 => p2.getName.equals(p.getName) && p2.getIn.equals(p.getIn)).orNull
      val o = slist2.find(p2 => p2.getName.equals(p.getName) && p2.getIn.equals(p.getIn)).orNull
      mergedParams = mergedParams :+ mergeParameter(c, o)
    })
    mergedParams
  }

  //Merges parameters based only on name and is required
  //Does not deal with query/path parameters and there is no guarantee
  //Parameters are also assumed to have the same name
  def mergeParameter(p1: Parameter, p2: Parameter): Parameter = {
    //If a parameter DNE with the same name then the resulting merge must not be required
    if(p1 == null) {
      p2.setRequired(false)
      p2
    }
    else if (p2 == null) {
      p1.setRequired(false)
      p1
    }
    else {
      //Merge the parameters
      assert(p1.getName.equals(p2.getName),"Parameters can only be merged if they have the same name")
      p1.setRequired(p1.getRequired && p2.getRequired)
      p1
    }
  }

  def toScalaList[T](list: java.util.List[T]): List[T] = {
    Option(list).getOrElse(new java.util.LinkedList[T]()).asScala.toList
  }
}

case class SwaggerDetails(service: String, version: String, endpoints: Map[EndpointTemplate, EndpointDetails])

trait SwaggerFetcher {
  def fetch(host: ServiceLocation) : Future[SwaggerDetails]
}
