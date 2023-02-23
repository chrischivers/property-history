package uk.co.thirdthing.views

import cats.syntax.all.*
import com.raquo.airstream.state.Var
import com.raquo.airstream.web.AjaxEventStream
import com.raquo.airstream.web.AjaxEventStream.{AjaxStatusError, AjaxStreamError}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.parser.*
import org.scalajs.dom
import org.scalajs.dom.{XMLHttpRequest, html}
import uk.co.thirdthing.model.Types.{ListingId, ListingSnapshot, Price}
import uk.co.thirdthing.utils.TimeUtils.*

import java.time.ZoneId
import scala.scalajs.js.URIUtils

object PageHeader:
  def render = header(
    cls := "masthead bg-primary text-white text-center",
    div(
      cls := "container d-flex align-items-center flex-column",
      h1(cls := "masthead-heading text-uppercase mb-0", "Rightmove Property History"),
      p(cls  := "masthead-subheading font-weight-light mb-0", "Discover more information about a Rightmove listing")
    )
  )
