namespace smithy4s.hello

use alloy#simpleRestJson
use smithy4s.meta#packedInputs

@packedInputs
@simpleRestJson
service PublicApiService {
  version: "1.0.0",
  operations: [GetHistoryOperation]
}

@http(method: "GET", uri: "/history/{listingId}", code: 200)
operation GetHistoryOperation {
  input: GetHistoryRequest,
  output: GetHistoryResponse,
  errors: [ListingNotFound]
}

structure GetHistoryRequest {
    @httpLabel
    @required
    listingId: Long
}

list HistoryRecordList {
  member: HistoryRecord
}

structure HistoryRecordDetails {
  price: Integer,
  transactionTypeId: Integer,
  visible: Boolean,
  status: String,
  rentFrequency: String,
  latitude: Double,
  longitude: Double,
  thumbnailUrl: String
}

structure HistoryRecord {
  @required
  listingId: Long,
  @required
  lastChange: Timestamp,
  @required
  propertyId: Long,
  @required
  dateAdded: Timestamp,
  @required
  details: HistoryRecordDetails,
  listingSnapshotId: Long
}

structure GetHistoryResponse {
  @required
  records: HistoryRecordList
}

@error("client")
@httpError(404)
structure ListingNotFound {}
