package jp.co.bizreach.trace.play24.filter

import javax.inject.Inject

import jp.co.bizreach.trace.ZipkinTraceServiceLike
import play.api.mvc.{Filter, Headers, RequestHeader, Result}

import scala.concurrent.Future
import scala.util.Failure

/**
 * A Zipkin filter.
 *
 * This filter is that reports how long a request takes to execute in Play as a server span.
 * The way to use this filter is following:
 * {{{
 * class Filters @Inject() (
 *   zipkin: ZipkinTraceFilter
 * ) extends DefaultHttpFilters(zipkin)
 * }}}
 *
 * @param tracer a Zipkin tracer
 */
class ZipkinTraceFilter @Inject() (tracer: ZipkinTraceServiceLike) extends Filter {

  import tracer.executionContext
  private val reqHeaderToSpanName: RequestHeader => String = ZipkinTraceFilter.ParamAwareRequestNamer

  def apply(nextFilter: (RequestHeader) => Future[Result])(req: RequestHeader): Future[Result] = {
    val serverSpan = tracer.serverReceived(
      spanName = reqHeaderToSpanName(req),
      span = tracer.newSpan(req.headers)((headers, key) => headers.get(key))
    )
    val result = nextFilter(req.copy(headers = new Headers(
      (req.headers.toMap.mapValues(_.headOption getOrElse "") ++ tracer.toMap(serverSpan)).toSeq
    )))
    result.onComplete {
      case Failure(t) => tracer.serverSend(serverSpan, "failed" -> s"Finished with exception: ${t.getMessage}")
      case _ => tracer.serverSend(serverSpan)
    }
    result
  }
}

object ZipkinTraceFilter {
  val ParamAwareRequestNamer: RequestHeader => String = { reqHeader =>
    import org.apache.commons.lang3.StringUtils
    val tags = reqHeader.tags
    val pathPattern = StringUtils.replace(tags.getOrElse(play.api.routing.Router.Tags.RoutePattern, reqHeader.path), "<[^/]+>", "")
    s"${reqHeader.method} - $pathPattern"
  }
}
