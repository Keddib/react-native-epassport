package com.nfcpassportreader.dto

data class DgsData(
  var DG1: String? = null,
  var DG2: String? = null,
  var DG7: String? = null,
  var DG11: String? = null,
)
data class NfcResult(
  var dgs: DgsData? = null,
  var sod: String? = null,
)
