package uk.co.thirdthing

import cats.effect.IO
import cats.syntax.all._
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.http4s.dom._
import org.scalajs.dom.html
import uk.co.thirdthing.model.Types.{ListingId, ListingSnapshot}
import com.raquo.airstream.web.AjaxEventStream
import com.raquo.airstream.web.AjaxEventStream.AjaxStreamError
import com.raquo.laminar.api.L._
import io.circe.{DecodingFailure, ParsingFailure}
import org.scalajs.dom
import io.circe.syntax._
import io.circe.parser._
import org.scalajs.dom.html.TableCell
import uk.co.thirdthing.utils.TimeUtils.InstantOps

import java.time.ZoneId

object UI {

  private def validateUrl(url: String): Option[ListingId] = {
    val regex = "^(https://|http://)?www.rightmove.co.uk/properties/([0-9]+)".r
    regex.findAllIn(url).matchData.flatMap(m => Option(m.group(2)).flatMap(_.toLongOption.map(ListingId(_)))).toList.headOption
  }

  private def toUrl(listingId: ListingId) =
    s"https://www.rightmove.co.uk/properties/$listingId"

  private def formatListingResults(results: List[ListingSnapshot]) = {
    if (results.isEmpty) div()
    else {
      div(
        "The following listings were found for that property",
        table(
          cls := "table",
          tr(
            th("Date Added"),
            th("Sale/Rental"),
            th("Status"),
            th("Price"),
            th("Link")
          ) +:
          results.map { ls =>
            tr(
              List(
                td(ls.dateAdded.value.atZone(ZoneId.of("UTC")).toLocalDate.toString).some,
                ls.details.transactionTypeId.map(tt => td(tt.string)),
                ls.details.status.map(s => td(s.value)),
                ls.details.price.map(p => td(p.value)),
                td(a(href := toUrl(ls.listingId), "[link]")).some
              ).flatten: _*
            )
          }
        )
      )
    }
  }

  def apply: ReactiveHtmlElement[html.Div] = {

    val validatedListingId: Var[Option[ListingId]] = Var(initial = None)
    val resultsVar: Var[List[ListingSnapshot]]     = Var(initial = List.empty)

    val urlEntryElem = div(
      label(cls := "form-label", "Enter Rightmove property link: "),
      input(
        cls <-- validatedListingId.signal.map(id => if (id.isDefined) "form-control is-valid" else "form-control"),
        onMountFocus,
        placeholder := "E.g. https://www.rightmove.co.uk/properties/xxxxxxxxx",
        onInput.mapToValue.map(validateUrl) --> validatedListingId
      )
    )

    def submitButton(listingId: ListingId): Div =
      div(
        button(
          typ("Submit"),
          "Submit",
          inContext { thisNode =>
            val $click = thisNode.events(onClick)
            val $response = $click.flatMap { _ =>
              thisNode.ref.remove()
              AjaxEventStream
                .get(
                  url = s"api/v1/history/${listingId.value}"
                )
                .map(r => parse(r.responseText).flatMap(_.as[List[ListingSnapshot]]).fold(throw _, identity))
                .recover {
                  case err: AjaxStreamError =>
                    dom.console.log(err.getMessage)
                    Some(List.empty[ListingSnapshot])

                  case err: io.circe.Error =>
                    dom.console.log(err.getMessage)
                    Some(List.empty[ListingSnapshot])
                }
            }
            List(
              $response --> resultsVar.updater[List[ListingSnapshot]] { case (_, next) => next }
            )
          }
        )
      )

    val buttonElem = div(
      child <-- validatedListingId.signal.map(_.fold(div())(submitButton))
    )
    val resultsElem = div(
      child <-- resultsVar.signal.map(r => formatListingResults(r))
    )

    div(
      urlEntryElem,
      buttonElem,
      resultsElem
    )

  }
}
