play-zipkin-tracing-play23
========

A library to add tracing capability to Play 2.3 based microservices.

## Setup

Add following dependency to `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "jp.co.bizreach" %% "play-zipkin-tracing-play23" % "1.2.0"
)
```

Add following configuration to `application.conf`:

```
trace {
  service-name = "zipkin-api-sample"

  zipkin {
    base-url = "http://localhost:9411"
    sample-rate = 0.1
  }
}

zipkin-trace-context {
  fork-join-executor {
    parallelism-factor = 20.0
    parallelism-max = 200
  }
}
```

## Usage

Create `Global` object with `ZipkinTraceFilter` as following and put it into the classpath root.

```scala
import com.stanby.trace.play23.filter.ZipkinTraceFilter
import play.api.GlobalSettings
import play.api.mvc.WithFilters

object Global extends WithFilters(new ZipkinTraceFilter()) with GlobalSettings
```

In the controller, trace action and calling another services as following:


```scala
package controllers

import jp.co.bizreach.trace.play23.TraceWS
import jp.co.bizreach.trace.play23.implicits.ZipkinTraceImplicits
import play.api.mvc.{Action, Controller}
import play.api.Play.current
import play.api.libs.json.Json
import scala.concurrent.ExecutionContext.Implicits.global

class ApiController extends Controller with ZipkinTraceImplicits {

  // Trace blocking action
  def test1 = Action { implicit request =>
    ZipkinTraceService.trace("sync"){ implicit traceData =>
      println("Hello World!")
      Ok(Json.obj("result" -> "ok"))
    }
  }

  // Trace async action
  def test2 = Action.async { implicit request =>
    ZipkinTraceService.traceFuture("async"){ implicit traceData =>
      Future {
        println("Hello World!")
        Ok(Json.obj("result" -> "ok"))
      }
    }
  }

  // Trace WS request
  def test3 = Action.async { implicit req =>
    TraceWS.url("ws", "http://localhost:9992/api/hello")
      .get().map { res => Ok(res.json) }
  }

}
```
