package uk.co.thirdthing

import cats.syntax.all.*
import com.raquo.airstream.state.Var
import com.raquo.airstream.web.AjaxEventStream
import com.raquo.airstream.web.AjaxEventStream.{AjaxStatusError, AjaxStreamError}
import com.raquo.laminar.api.L.*
import com.raquo.laminar.nodes.ReactiveHtmlElement
import io.circe.parser.*
import org.scalajs.dom
import org.scalajs.dom.{XMLHttpRequest, html}
import uk.co.thirdthing.model.Types.{ListingId, ListingSnapshot, Price, PropertyLookupDetails, Transaction}
import uk.co.thirdthing.utils.TimeUtils.*
import uk.co.thirdthing.views.PageHeader

import java.time.ZoneId
import scala.scalajs.js.URIUtils

object UI:

  private val validatedListingId: Var[Option[ListingId]]     = Var(initial = None)
  private val resultsVar: Var[Option[PropertyLookupDetails]] = Var(initial = Option.empty)
  private val errorVar: Var[Option[String]]                  = Var(None)
  private val submittedVar: Var[Boolean]                     = Var(false)

  private def validateUrl(url: String): Option[ListingId] =
    val regex = "^(https://|http://)?www.rightmove.co.uk/properties/([0-9]+)".r
    regex
      .findAllIn(url)
      .matchData
      .flatMap(m => Option(m.group(2)).flatMap(_.toLongOption.map(ListingId(_))))
      .toList
      .headOption

  private def toUrl(listingId: ListingId) =
    s"https://www.rightmove.co.uk/properties/$listingId"

  private def formatListingResults(results: Option[PropertyLookupDetails]) =
    results.fold(div()) { details =>
      div(
        details.fullAddress.fold(div())(add => div(s"Property full address is ${add.value}")),
        div(
          "The following listings were found for that property",
          table(
            cls := "table",
            tr(
              th(""),
              th("Date Added"),
              th("Sale/Rental"),
              th("Status"),
              th("Price"),
              th("Link")
            ) +:
              details.listingRecords.map(formatListingsRow)
          )
        ),
        div(
          "The following sale transactions were found for that property",
          table(
            cls := "table",
            tr(
              th("Date"),
              th("Price"),
              th("Tenure")
            ) +:
              details.transactions.map(formatTransaction)
          )
        )
      )
    }

  private def formatListingsRow(ls: ListingSnapshot) =
    val unknownTd = td("unknown")
    def link(mod: Modifier[ReactiveHtmlElement[html.Anchor]]) =
      a(href := toUrl(ls.listingId), target := "_blank", mod)
    tr(
      List(
        td(link(img(src := thumbnailUrl(ls), height := "150px", width := "auto"))),
        td(ls.dateAdded.value.toLocalDate.toString),
        ls.details.transactionTypeId.fold(unknownTd)(tt => td(tt.string)),
        ls.details.status.fold(unknownTd)(s => td(s.value)),
        ls.details.price.fold(unknownTd)(p => td(formatPrice(p))),
        td(link("[link]"))
      )*
    )

  private def formatTransaction(tx: Transaction) =
    val unknownTd = td("unknown")
    tr(
      List(
        td(tx.date.toString),
        td(tx.price.toString),
        tx.tenure.fold(unknownTd)(ten => td(ten.value))
      )*
    )

  private def formatPrice(price: Price) =
    String.format("£%,d", price.value)

  private val urlEntryElem = div(
    label(cls := "form-label", "Enter Rightmove property link: "),
    input(
      cls <-- validatedListingId.signal.map(id => if id.isDefined then "form-control is-valid" else "form-control"),
      onMountFocus,
      placeholder := "E.g. https://www.rightmove.co.uk/properties/xxxxxxxxx",
      onInput.mapToValue.map(validateUrl) --> validatedListingId,
      onInput.mapTo(false) --> submittedVar,
      onInput.mapTo(None) --> errorVar
    )
  )

  private val errorElem = div(
    child <-- errorVar.signal.map(_.fold(div())(err => div(err)))
  )

  private val buttonElem = div(
    child <-- validatedListingId.signal.map(_.fold(div())(submitButton))
  )
  private val resultsElem = div(
    child <-- resultsVar.signal.map(formatListingResults)
  )

  private def submitButton(listingId: ListingId): Div =
    div(
      button(
        typ("Submit"),
        "Submit",
        disabled <-- submittedVar.signal,
        inContext { thisNode =>
          val $click = thisNode.events(onClick)
          val $response = $click.flatMap { _ =>
            submittedVar.set(true)
            makeListingsRequest(listingId)
          }
          List(
            $response --> { updatedValue => resultsVar.set(Some(updatedValue)) }
          )
        }
      )
    )

  private def thumbnailUrl(snapshot: ListingSnapshot): String =
    val queryParams =
      snapshot.details.thumbnailUrl.fold(s"listingId=${snapshot.listingId.value}")(url =>
        s"thumbnailUrl=${URIUtils.encodeURI(url.value)}"
      )
    s"api/v1/thumbnail?$queryParams"

  private def makeListingsRequest(listingId: ListingId): EventStream[PropertyLookupDetails] =
    AjaxEventStream
      .get(url = s"api/v1/history/${listingId.value}")
      .flatMap(parseResponse)
      .recover {
        case AjaxStatusError(_, 404, _) =>
          errorVar.set("No listings found at this location".some)
          None
        case _: AjaxStreamError =>
          errorVar.set("Something went wrong".some)
          None
        case _: io.circe.Error =>
          errorVar.set("Something went wrong".some)
          None
      }

  private def parseResponse(response: XMLHttpRequest): EventStream[PropertyLookupDetails] =
    EventStream.fromTry(
      parse(response.responseText).flatMap(_.as[PropertyLookupDetails]).toTry
    )

  def apply: ReactiveHtmlElement[html.Div] =
    div(
      PageHeader.render,
      urlEntryElem,
      buttonElem,
      errorElem,
      resultsElem
    )
