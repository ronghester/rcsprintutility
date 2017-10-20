cordova.define("com.github.michael79bxl.zbtprinter.WoosimBluetoothPrinter", function(require, exports, module) { var exec = require('cordova/exec');

exports.lpPrint = function(mac, str, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'WoosimBluetoothPrinter', 'lpPrint', [mac, str]);
};

exports.lpFind = function(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'WoosimBluetoothPrinter', 'lpFind', []);
};


});
