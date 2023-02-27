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

list ListingRecordList {
  member: ListingRecord
}

list TransactionRecordList {
  member: TransactionRecord
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

structure ListingRecord {
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

structure TransactionRecord {
  @required
  price: Integer,
  @required
  date: String,
  tenure: String
}

structure GetHistoryResponse {
  fullAddress: String,
  postcode: String,
  @required
  listingRecords: ListingRecordList
  @required
  transactions: TransactionRecordList
}

@error("client")
@httpError(404)
structure ListingNotFound {}
