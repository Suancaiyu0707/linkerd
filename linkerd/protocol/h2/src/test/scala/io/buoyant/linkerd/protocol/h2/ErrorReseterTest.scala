package io.buoyant.linkerd.protocol.h2

import com.twitter.finagle.buoyant.Dst
import com.twitter.finagle.{Status => _, _}
import com.twitter.finagle.buoyant.h2._
import com.twitter.finagle.naming.buoyant.RichNoBrokersAvailableException
import com.twitter.util.{Future, Throw}
import io.buoyant.linkerd.protocol.h2.ErrorReseter.H2ResponseException
import io.buoyant.router.RoutingFactory
import io.buoyant.test.Awaits
import org.scalatest.FunSuite

class ErrorReseterTest extends FunSuite with Awaits {

  test("coerces RoutingFactory.UnknownDst to Reset.Refused") {
    val service = ErrorReseter.filter.andThen(Service.mk[Request, Response] { req =>
      Future.exception(RoutingFactory.UnknownDst(req, "yodles"))
    })

    val req = Request("http", Method.Get, "hihost", "/", Stream.empty())
    assert(await(service(req).liftToTry) == Throw(Reset.Refused))
  }

  test("returns H2ResponseExceptions as responses") {
    val service = ErrorReseter.filter.andThen(Service.mk[Request, Response] { _ =>
      Future.exception(H2ResponseException(Response(Status.Cowabunga, Stream.empty())))
    })

    val req = Request("http", Method.Get, "hihost", "/", Stream.empty())
    val rsp = await(service(req))
    assert(rsp.status == Status.Cowabunga)
    assert(!rsp.headers.contains("l5d-err"))
    assert(rsp.stream.isEmpty)
  }

  test("passes through arbitrary exceptions") {
    val yodles = Failure("yodles")
    val service = ErrorReseter.filter.andThen(Service.mk[Request, Response] { _ =>
      Future.exception(yodles)
    })
    val req = Request("http", Method.Get, "hihost", "/", Stream.empty())
    assert(await(service(req).liftToTry) == Throw(yodles))
  }

  test("passes through responses") {
    val service = ErrorReseter.filter.andThen(Service.mk[Request, Response] { _ =>
      Future.value(Response(Status.Cowabunga, Stream.empty()))
    })
    val req = Request("http", Method.Get, "hihost", "/", Stream.empty())
    val rsp = await(service(req))
    assert(rsp.status == Status.Cowabunga)
    assert(!rsp.headers.contains("l5d-err"))
    assert(rsp.stream.isEmpty)
  }

  test("response from exceptions should have l5d-err header"){
    val service = ErrorReseter.filter.andThen(Service.mk[Request, Response]{_ =>
      Future.exception(new RichNoBrokersAvailableException(Dst.Path.empty,None))
    })
    val req = Request("http", Method.Get, "hihost", "/", Stream.empty())
    val resp = await(service(req))
    assert(resp.headers.contains("l5d-err"))
  }
}
