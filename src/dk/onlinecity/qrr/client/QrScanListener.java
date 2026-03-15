/*
 * Copyright (C) 2011 OnlineCity
 * Licensed under the MIT license, which can be read at: http://www.opensource.org/licenses/mit-license.php
 * Simplified for reusable QR scanner - KISS/SOLID
 */

package dk.onlinecity.qrr.client;

/**
 * Callback for QR scanner. Implement to handle decoded text and navigation.
 */
public interface QrScanListener {
    void showCamera();
    void takeSnapshot();
    void showResult(String text);
    void exit();
}
