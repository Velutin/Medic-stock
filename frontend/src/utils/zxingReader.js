/**
 * Single setup of the ZXing WASM reader, shared by the camera (BarcodeScanner) and by the consumption sheet
 * reading (sheetReader). It is the C++ ZXing compiled to WebAssembly: on the Data Matrix of the implant
 * labels it is much stronger than the JavaScript reader, which is why the camera uses it too.
 *
 * The .wasm file is served by the application itself (no external CDN), so reading works on the hospital
 * network without internet access.
 */
import { prepareZXingModule, readBarcodes } from 'zxing-wasm/reader';
import zxingWasmUrl from 'zxing-wasm/reader/zxing_reader.wasm?url';

prepareZXingModule({
  overrides: { locateFile: (path, prefix) => (path.endsWith('.wasm') ? zxingWasmUrl : prefix + path) },
});

/** Formats printed on the implant labels: GS1 Data Matrix / QR code, GS1-128 and EAN/UPC barcodes. */
export const LABEL_FORMATS = ['DataMatrix', 'QRCode', 'Code128', 'EAN13', 'EAN8', 'UPCA'];

export { readBarcodes };
