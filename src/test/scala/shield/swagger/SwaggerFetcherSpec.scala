package shield.swagger

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import io.swagger.models.parameters.{Parameter, PathParameter, QueryParameter}
import io.swagger.models.{HttpMethod, Operation, Swagger, Tag}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers, WordSpecLike}
import shield.routing.{EndpointDetails, EndpointTemplate, Param, Path}
import spray.http.{HttpMethods, MediaType}

import scala.collection.JavaConverters._

class SwaggerFetcherSpec extends TestKit(ActorSystem("testSystem", ConfigFactory.parseString(
  """
    |akka.scheduler.implementation = shield.akka.helpers.VirtualScheduler
  """.stripMargin).withFallback(ConfigFactory.load())))
with WordSpecLike
with MustMatchers
with BeforeAndAfterEach
with BeforeAndAfterAll {

  "SwaggerFetcher" must {

    def detailsFromSwagger(swagger: Swagger): EndpointDetails = {
      EndpointDetails(
        Set[Param](),
        Set[MediaType](),
        Set[MediaType](),
        Set[String](),
        Set[String](),
        swagger
      )
    }

    val parameterA = new QueryParameter()
    parameterA.setName("paramA")
    parameterA.setRequired(true)

    val parameterB = new PathParameter()
    parameterB.setName("paramB")
    parameterB.setRequired(true)

    val parameterC = new PathParameter()
    parameterC.setName("paramD")
    parameterC.setRequired(false)

    val parameterD = new PathParameter()
    parameterD.setName("paramA")
    parameterD.setRequired(false)

    val getA = new io.swagger.models.Path().get(new Operation().parameter(parameterA))
    getA.addParameter(parameterA)
    val getB = new io.swagger.models.Path().get(new Operation().parameter(parameterB).parameter(parameterA))
    getB.addParameter(parameterB)
    getB.addParameter(parameterA)
    val getC = new io.swagger.models.Path().post(new Operation().parameter(parameterA))
    getC.addParameter(parameterA)
    val getD = new io.swagger.models.Path().get(new Operation().parameter(parameterA).parameter(parameterC).parameter(parameterD))
    getD.addParameter(parameterC)
    getD.addParameter(parameterA)
    getD.addParameter(parameterD)

    val specA = new Swagger().paths(Map[String,io.swagger.models.Path]("/fizzbuzz" -> getA).asJava).tag(new Tag().name("tag1"))
    val specB = new Swagger().paths(Map[String,io.swagger.models.Path]("/fizzbuzz" -> getB).asJava).tag(new Tag().name("tag2"))
    val specC = new Swagger().paths(Map[String,io.swagger.models.Path]("/fizzbuzz" -> getC).asJava).tag(new Tag().name("tag1"))
    val specD = new Swagger().paths(Map[String,io.swagger.models.Path]("/fizzbuzz" -> getD).asJava)

    val groupA = EndpointTemplate(HttpMethods.GET,Path("/fizzbuzz")) -> detailsFromSwagger(specA)
    val groupB = EndpointTemplate(HttpMethods.GET,Path("/fizzbuzz")) -> detailsFromSwagger(specB)
    val groupC = EndpointTemplate(HttpMethods.POST,Path("/fizzbuzz")) -> detailsFromSwagger(specC)
    val groupD = EndpointTemplate(HttpMethods.GET,Path("/fizzbuzz")) -> detailsFromSwagger(specD)


    "merge tags by addition" in {
      val combinedSwagger = SwaggerFetcher.swaggerSpec(List[(EndpointTemplate,EndpointDetails)](groupA,groupB,groupC))
      val expectedTags = Set[String]("tag1","tag2")
      val returnedTags = combinedSwagger.getTags
      returnedTags.size() mustBe 2
      val filtered = expectedTags.filterNot(tag => returnedTags.asScala.map(_.getName).contains(tag))

        filtered.isEmpty mustBe true
    }


    "merge method by addition" in {
      val combinedSwagger = SwaggerFetcher.swaggerSpec(List[(EndpointTemplate,EndpointDetails)](groupA,groupB,groupC))
      val returnedPaths = combinedSwagger.getPaths
      val expectedMethods = Set[HttpMethod](HttpMethod.GET, HttpMethod.POST)
      returnedPaths.size() mustBe 1

      returnedPaths.get("/fizzbuzz").getOperationMap.size() mustBe 2
      val filtered = expectedMethods.filterNot(method => returnedPaths.get("/fizzbuzz").getOperationMap.asScala.map(_._1).exists(m => method.equals(m)))
      filtered.isEmpty mustBe true

    }

    "merge swagger docs and parameters" in {
      val combinedParameters = SwaggerFetcher.swaggerSpec(List[(EndpointTemplate,EndpointDetails)](groupA,groupB,groupC,groupD)).getPath("/fizzbuzz").getParameters().asScala
      val expectedParameters = Map(
        "paramA" -> true,
        "paramB" -> false,
        "paramD" -> false
      )

      val filtered = expectedParameters.filterNot(p => combinedParameters.exists(returnedParam => returnedParam.getName.equals(p._1) && returnedParam.getRequired == p._2))
      combinedParameters.size mustBe 4
      filtered.isEmpty mustBe true
    }

    "merge operations and parameters" in {
      val a = SwaggerFetcher.swaggerSpec(List[(EndpointTemplate,EndpointDetails)](groupA,groupB,groupC,groupD))
        val combinedParameters = a.getPath("/fizzbuzz").getGet.getParameters().asScala
      val expectedParameters = Map(
        "paramA" -> true,
        "paramB" -> false,
        "paramD" -> false
      )

      val filtered = expectedParameters.filterNot(p => combinedParameters.exists(returnedParam => returnedParam.getName.equals(p._1) && returnedParam.getRequired == p._2))
      combinedParameters.size mustBe 4
      filtered.isEmpty mustBe true
    }

    "differentiate parameters that are located in different parts of the request(path vs query)" in {
      val combinedSwagger= SwaggerFetcher.swaggerSpec(List[(EndpointTemplate,EndpointDetails)](groupA,groupB,groupC,groupD))
      val combinedParameters = combinedSwagger.getPath("/fizzbuzz").getGet.getParameters().asScala
      combinedParameters.count(param => param.getName.equals("paramA")) mustBe 2
    }

    "result in an empty swagger doc when no upstreams are present" in {
      val combinedSwagger = SwaggerFetcher.swaggerSpec(List[(EndpointTemplate,EndpointDetails)]())
      combinedSwagger mustBe new Swagger()
    }
  }
}
