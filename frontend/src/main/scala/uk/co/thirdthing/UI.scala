package uk.co.thirdthing

import cats.syntax.all._
import com.raquo.airstream.state.Var
import com.raquo.airstream.web.AjaxEventStream
import com.raquo.airstream.web.AjaxEventStream.AjaxStreamError
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.parser._
import org.scalajs.dom
import org.scalajs.dom.{XMLHttpRequest, html}
import uk.co.thirdthing.model.Types.{ListingId, ListingSnapshot}

import java.time.ZoneId

object UI {

  private val validatedListingId: Var[Option[ListingId]] = Var(initial = None)
  private val resultsVar: Var[List[ListingSnapshot]]     = Var(initial = List.empty)

  private def validateUrl(url: String): Option[ListingId] = {
    val regex = "^(https://|http://)?www.rightmove.co.uk/properties/([0-9]+)".r
    regex.findAllIn(url).matchData.flatMap(m => Option(m.group(2)).flatMap(_.toLongOption.map(ListingId(_)))).toList.headOption
  }

  private def toUrl(listingId: ListingId) =
    s"https://www.rightmove.co.uk/properties/$listingId"

  private def formatListingResults(results: List[ListingSnapshot]) =
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

  private val urlEntryElem = div(
    label(cls := "form-label", "Enter Rightmove property link: "),
    input(
      cls <-- validatedListingId.signal.map(id => if (id.isDefined) "form-control is-valid" else "form-control"),
      onMountFocus,
      placeholder := "E.g. https://www.rightmove.co.uk/properties/xxxxxxxxx",
      onInput.mapToValue.map(validateUrl) --> validatedListingId
    )
  )

  private val buttonElem = div(
    child <-- validatedListingId.signal.map(_.fold(div())(submitButton))
  )
  private val resultsElem = div(
    child <-- resultsVar.signal.map(r => formatListingResults(r))
  )

  private def submitButton(listingId: ListingId): Div =
    div(
      button(
        typ("Submit"),
        "Submit",
        inContext { thisNode =>
          val $click = thisNode.events(onClick)
          val $response = $click.flatMap { _ =>
            thisNode.ref.remove()
            makeRequest(listingId)
          }
          List(
            $response --> { updatedValue => resultsVar.set(updatedValue) }
          )
        }
      )
    )

  private def makeRequest(listingId: ListingId): EventStream[List[ListingSnapshot]] =
    AjaxEventStream
      .get(
        url = s"api/v1/history/${listingId.value}"
      )
      .flatMap(parseResponse)
      .recover {
        case err: AjaxStreamError =>
          dom.console.log(err.getMessage)
          Some(List.empty[ListingSnapshot])

        case err: io.circe.Error =>
          dom.console.log(err.getMessage)
          Some(List.empty[ListingSnapshot])
      }

  private def parseResponse(response: XMLHttpRequest): EventStream[List[ListingSnapshot]] =
    EventStream.fromTry(parse(response.responseText).flatMap(_.hcursor.downField("records").as[List[ListingSnapshot]]).toTry)

  def apply: ReactiveHtmlElement[html.Div] =
    div(
      urlEntryElem,
      buttonElem,
      resultsElem
    )

}
