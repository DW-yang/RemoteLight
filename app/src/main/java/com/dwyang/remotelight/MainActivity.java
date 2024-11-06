package com.dwyang.remotelight;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.Manifest;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    //创建UI对象
    private SeekBar brightness;
    private TextView current_brightness;
    //创建蓝牙对象
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bleGatt;
    private BluetoothGattService bleService;
    private BluetoothGattCharacteristic bleCharacteristic;
    private boolean isDeviceFound = false;
    private static final UUID SERVICE_UUID = UUID.fromString("3fd76e07-c468-4042-9365-52d90025f661");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("e3aebfb6-9a19-40d8-86fb-24edd872232d");

    private final int mRequestCode = 0x10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestPermissions();
        initView();
    }

    /**
     * 申请权限
     * <p> 如果申请权限成功则会进入{@code initBLE()}函数进行蓝牙的初始化，申请失败将会退出程序
     */
    private void requestPermissions() {
        @SuppressLint("InlinedApi") String[] permissions = new String[]{Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        List<String>  mPermissionList = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permission);
            }
        }
        if (!mPermissionList.isEmpty()){
            ActivityCompat.requestPermissions(this,permissions,mRequestCode);
        }else {
            // 蓝牙操作必须在拥有权限后执行
            initBLE(this);
            scanLeDevice();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean hasPermissionDismiss = false;//有权限没有通过
        if (mRequestCode == requestCode) {
            for (int grantResult : grantResults) {
                if (grantResult == -1) {
                    hasPermissionDismiss = true;
                    break;
                }
            }
        }
        if (hasPermissionDismiss) {//如果有没有被允许的权限
            Toast.makeText(this, "没有权限,请允许精确位置和发现附近设备的权限", Toast.LENGTH_LONG).show();
            finish();
        } else {
            initBLE(this);
            scanLeDevice();
        }
    }

    /**
     * 初始化蓝牙
     * <p> 请注意：初始化蓝牙并不会对蓝牙是否成功开启作判断，在进行蓝牙通信前仍需检查蓝牙是否开启
     * @param context 上下文
     */
    @SuppressLint("MissingPermission")
    private void initBLE(@NonNull Context context){
        btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if(btManager == null){
            return;
        }
        btAdapter = btManager.getAdapter();
        if(!btAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivity(enableBtIntent);
        }
    }

    /**
     * 扫描BLE设备
     */
    @SuppressLint("MissingPermission")
    private void scanLeDevice() {
        // 检查蓝牙是否开启
        if(!btAdapter.isEnabled()){
            Toast.makeText(this, "蓝牙未打开", Toast.LENGTH_LONG).show();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivity(enableBtIntent);
        }else {
            bleScanner = btAdapter.getBluetoothLeScanner();
            bleScanner.startScan(leScanCallback);
        }
    }

    /**
     * BLE设备扫描回调
     * <p>在扫描到设备时，会把设备传给<code>connectLeDevice</code>函数进行处理
     */
    private final ScanCallback leScanCallback =
            new ScanCallback() {
                @SuppressLint("MissingPermission")
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if(Objects.equals(result.getDevice().getName(), "ESP32C3 Light")){
                        if(!isDeviceFound)
                            connectLeDevice(result.getDevice());
                    }
                }
            };

    @SuppressLint("MissingPermission")
    private void connectLeDevice(BluetoothDevice device){
        isDeviceFound = true;
        bleScanner.stopScan(leScanCallback);
        Toast.makeText(this, "已发现ESP32C3 Light", Toast.LENGTH_LONG).show();
        bleGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,int newState){
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices();
            }
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                isDeviceFound = false;
                bleGatt = null;
                bleService = null;
                bleCharacteristic = null;
                scanLeDevice();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            bleService = bleGatt.getService(SERVICE_UUID);
            bleCharacteristic = bleService.getCharacteristic(CHARACTERISTIC_UUID);
        }
    };

    /**
     * 初始化View控件
     */
    private void initView(){
        brightness = findViewById(R.id.brightness);
        current_brightness = findViewById(R.id.current_brightness);
        //重写SeekBar回调函数
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint({"SetTextI18n", "MissingPermission"})
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(bleCharacteristic != null) {
                    current_brightness.setText(getString(R.string.current_brightness) + i);
                    bleGatt.writeCharacteristic(bleCharacteristic, String.valueOf(i).getBytes(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                else{
                    current_brightness.setText("蓝牙未连接！");
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(bleCharacteristic != null) {
                    current_brightness.setText(getString(R.string.current_brightness) + seekBar.getProgress());
                    bleGatt.writeCharacteristic(bleCharacteristic, String.valueOf(seekBar.getProgress()).getBytes(), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }
                else{
                    current_brightness.setText("蓝牙未连接！");
                    scanLeDevice();
                }
            }
        });
    }
}