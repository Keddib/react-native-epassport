export enum NfcPassportReaderEvent {
  DOCUMENT_READING_PROGRESS = 'onDocumentReadingProgress',
  NFC_STATE_CHANGED = 'onNfcStateChanged',
}

const DOCUMENT_READING_PROGRESS = {
  AUTH: 'AUTH',
  SOD: 'SOD',
  DG1: 'DG1',
  DG2: 'DG2',
  DG7: 'DG7',
  DG11: 'DG11',
} as const;

export type DocumentReadingProgress = keyof typeof DOCUMENT_READING_PROGRESS;

export type MRZKey = string;
export type NfcResult = {
  dgs: {
    DG1?: string;
    DG2?: string;
    DG7?: string;
    DG11?: string;
  };
  sod?: string;
};

export type CustomMessages = {
  requestPresentPassport: string;
  authenticatingWithPassport: string;
  readingDataGroup: string;
  error: string;
  successfulRead: string;
};
