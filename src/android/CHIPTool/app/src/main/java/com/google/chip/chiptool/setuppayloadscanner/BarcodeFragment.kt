/*
 *   Copyright (c) 2020 Project CHIP Authors
 *   All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.google.chip.chiptool.setuppayloadscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.fragment.app.Fragment
import chip.setuppayload.SetupPayloadParser
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.chip.chiptool.R
import com.google.chip.chiptool.util.FragmentUtil
import java.io.IOException

/** Launches the camera to scan for QR code. */
class BarcodeFragment : Fragment(), CHIPBarcodeProcessor.BarcodeDetectionListener {

    private var cameraSource: CameraSource? = null
    private var cameraSourceView: CameraSourceView? = null
    private var barcodeDetector: BarcodeDetector? = null
    private var cameraStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasCameraPermission()) {
            requestCameraPermission()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.barcode_fragment, container, false).apply {
            cameraSourceView = findViewById(R.id.camera_view)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeBarcodeDetectorAndCamera()
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        if (hasCameraPermission() && !cameraStarted) {
            startCamera()
        }
    }

    private fun initializeBarcodeDetectorAndCamera() {
        barcodeDetector?.let { detector ->
            if (!detector.isOperational) {
                showCameraUnavailableAlert()
            }
            return
        }

        val context = requireContext()
        barcodeDetector = BarcodeDetector.Builder(context).build().apply {
            setProcessor(CHIPBarcodeProcessor(this@BarcodeFragment))
        }
        cameraSource = CameraSource.Builder(context, barcodeDetector)
            .setFacing(CameraSource.CAMERA_FACING_BACK)
            .setAutoFocusEnabled(true)
            .setRequestedFps(30.0f)
            .build()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                showCameraPermissionAlert()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun handleScannedQrCode(barcode: Barcode) {
        Handler(Looper.getMainLooper()).post {
            stopCamera()

            val payload = SetupPayloadParser().parseQrCode(barcode.displayValue)
            val deviceInfo = CHIPDeviceInfo(
                payload.version,
                payload.vendorId,
                payload.productId,
                payload.setupPinCode
            )
            FragmentUtil.getHost(this, Callback::class.java)?.onCHIPDeviceInfoReceived(deviceInfo)
        }
    }

    override fun onPause() {
        super.onPause()
        stopCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSourceView?.release()
    }

    private fun showCameraPermissionAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.camera_permission_missing_alert_title)
            .setMessage(R.string.camera_permission_missing_alert_subtitle)
            .setPositiveButton(R.string.camera_permission_missing_alert_try_again) { _, _ ->
                requestCameraPermission()
            }
            .setCancelable(false)
            .create()
            .show()
    }

    private fun showCameraUnavailableAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.camera_unavailable_alert_title)
            .setMessage(R.string.camera_unavailable_alert_subtitle)
            .setPositiveButton(R.string.camera_unavailable_alert_exit) { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .create()
            .show()
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun startCamera() {
        try {
            cameraSourceView?.start(cameraSource)
            cameraStarted = true
        } catch (e: IOException) {
            Log.e(TAG, "Unable to start camera source.", e)
        }
    }

    private fun stopCamera() {
        cameraSourceView?.stop()
        cameraStarted = false
    }

    private fun hasCameraPermission(): Boolean {
        return (PackageManager.PERMISSION_GRANTED
            == checkSelfPermission(requireContext(), Manifest.permission.CAMERA))
    }

    private fun requestCameraPermission() {
        val permissions = arrayOf(Manifest.permission.CAMERA)
        requestPermissions(permissions, REQUEST_CODE_CAMERA_PERMISSION)
    }

    /** Interface for notifying the host. */
    interface Callback {
        /** Notifies host of the [CHIPDeviceInfo] from the scanned QR code. */
        fun onCHIPDeviceInfoReceived(deviceInfo: CHIPDeviceInfo)
    }

    companion object {
        private const val TAG = "BarcodeFragment"
        private const val REQUEST_CODE_CAMERA_PERMISSION = 100;

        @JvmStatic fun newInstance() = BarcodeFragment()
    }
}
