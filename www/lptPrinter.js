cordova.define("com.github.michael79bxl.zbtprinter.LineBluetoothPrinter", function(require, exports, module) { var exec = require('cordova/exec');

exports.lpPrint = function(mac, str, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'LineBluetoothPrinter', 'lpPrint', [mac, str]);
};
exports.lpLRUPrint = function(mac, str, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'LineBluetoothPrinter', 'lpLRUPrint', [mac, str]);
};
exports.lpFind = function(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'LineBluetoothPrinter', 'lpFind', []);
};


});
