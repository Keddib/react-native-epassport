import CoreNFC
import Foundation
import OpenSSL
import React
import UIKit

struct NFCCustomMessages {
    let requestPresentPassport: String
    let successfulRead: String
    let authenticatingWithPassport: String
    let readingDataGroup: String
    let error: String?

    init(dictionary: NSDictionary?) {
        self.requestPresentPassport = dictionary?["requestPresentPassport"] as? String ?? "Hold your iPhone near an NFC-enabled document."
        self.successfulRead = dictionary?["successfulRead"] as? String ?? "Document Successfully Read."
        self.authenticatingWithPassport = dictionary?["authenticatingWithPassport"] as? String ?? "Authenticating with document..."
        self.readingDataGroup = dictionary?["readingDataGroup"] as? String ?? "Read Data"
        self.error = dictionary?["error"] as? String
    }
}

@objc(NfcPassportReader)
class NfcPassportReader: NSObject {
  private let passportReader = PassportReader()
  private let passportUtil = PassportUtil()

  @objc
  static func requiresMainQueueSetup() -> Bool {
    return true
  }

  @objc func isNfcSupported(
    _ resolve: @escaping RCTPromiseResolveBlock, rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    if #available(iOS 13.0, *) {
      resolve(NFCNDEFReaderSession.readingAvailable)
    } else {
      resolve(false)
    }
  }

  @objc func startReading(
    mrzKey: String, customMessages: [String: String]?,
    resolver resolve: @escaping RCTPromiseResolveBlock,
    rejecter reject: @escaping RCTPromiseRejectBlock
  ) {
    let messages = NFCCustomMessages(dictionary: customMessages as NSDictionary?)
      let customMessageHandler: (NFCViewDisplayMessage) -> String? = { [weak self] displayMessage in
        guard let self = self else { return "" }
        switch displayMessage {
        case .requestPresentPassport:
            return messages.requestPresentPassport
        case .successfulRead:
            return messages.successfulRead
        case .authenticatingWithPassport:
          return messages.authenticatingWithPassport
        case .readingDataGroupProgress(let dataGroup, let progress):
          let progressString = self.handleProgress(percentualProgress: progress)
          return "\(messages.readingDataGroup) \(dataGroup) ...\n\(progressString)"
        case .error(let error):
            return messages.error ?? error.errorDescription
        default:
          return ""
        }
      }
    if mrzKey != nil {
      Task {
        do {
          let passport = try await self.passportReader.readPassport(
            mrzKey: mrzKey,
            tags: [.DG1, .DG2, .DG7, .DG11, .SOD],
            customDisplayMessage: customMessageHandler
          )
          print("passport: \(passport)")
          var dgsDict: [String: Any] = [:]

          // DG1 dict format
          if let dg1 = passport.dataGroupsRead[.DG1] as? DataGroup1 {
              dgsDict["DG1"] = binToHexRep(dg1.data)
          }

          // DG2 dict format
          if let dg2 = passport.dataGroupsRead[.DG2] as? DataGroup2 {
              dgsDict["DG2"] = binToHexRep(dg2.data)
          }

          // DG7 dict format
          if let dg7 = passport.dataGroupsRead[.DG7] as? DataGroup7 {
              dgsDict["DG7"] = binToHexRep(dg7.data)
          }

          // DG11 dict format
          if let dg11 = passport.dataGroupsRead[.DG11] as? DataGroup11 {
              dgsDict["DG11"] = binToHexRep(dg11.data)
          }

          var result: [String: Any] = [
              "dgs": dgsDict
          ]

          if let sod = passport.dataGroupsRead[.SOD] {
              result["sod"] = binToHexRep(sod.data)
          }
          resolve(result)
        } catch {
          reject("ERROR_READ_PASSPORT", "Error reading passport", nil)
        }
      }
    } else {
      reject("ERROR_INVALID_BACK_KEY", "Invalid bac key", nil)
    }
  }

  private func handleProgress(percentualProgress: Int) -> String {
    let barWidth = 10
    let completedWidth = Int(Double(barWidth) * Double(percentualProgress) / 100.0)
    let remainingWidth = barWidth - completedWidth

    let completedBar = String(repeating: "🔵", count: completedWidth)
    let remainingBar = String(repeating: "⚪️", count: remainingWidth)

    return "\(completedBar)\(remainingBar)"
  }
}
