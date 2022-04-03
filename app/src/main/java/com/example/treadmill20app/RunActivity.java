package com.example.treadmill20app;


import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.treadmill20app.BtServices.BleFtmsService;
import com.example.treadmill20app.BtServices.BleHeartRateService;
import com.example.treadmill20app.BtServices.GattActions;
import com.example.treadmill20app.models.WorkoutEntry;
import com.example.treadmill20app.utils.TypeConverter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Locale;

import de.siegmar.fastcsv.reader.CsvParser;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;

import static com.example.treadmill20app.BtServices.GattActions.ACTION_GATT_HEART_RATE_EVENTS;
import static com.example.treadmill20app.BtServices.GattActions.ACTION_GATT_FTMS_EVENTS;
import static com.example.treadmill20app.BtServices.GattActions.COMMAND_CHAR;
import static com.example.treadmill20app.BtServices.GattActions.CONTROL_TYPE;
import static com.example.treadmill20app.BtServices.GattActions.CONTROL_VALUE;
import static com.example.treadmill20app.BtServices.GattActions.HR_EVENT;
import static com.example.treadmill20app.BtServices.GattActions.FTMS_EVENT;
import static com.example.treadmill20app.BtServices.GattActions.HEART_RATE_DATA;
import static com.example.treadmill20app.BtServices.GattActions.INCLINATION_DATA;
import static com.example.treadmill20app.BtServices.GattActions.INCL_INCREMENT;
import static com.example.treadmill20app.BtServices.GattActions.INSTANT_SPEED_DATA;
import static com.example.treadmill20app.BtServices.GattActions.MAX_INCL;
import static com.example.treadmill20app.BtServices.GattActions.MAX_SPEED;
import static com.example.treadmill20app.BtServices.GattActions.MIN_INCL;
import static com.example.treadmill20app.BtServices.GattActions.MIN_SPEED;
import static com.example.treadmill20app.BtServices.GattActions.SPEED_INCREMENT;
import static com.example.treadmill20app.BtServices.GattActions.STATUS_TYPE;
import static com.example.treadmill20app.BtServices.GattActions.STATUS_VALUE;
import static com.example.treadmill20app.BtServices.GattActions.TOTAL_DISTANCE_DATA;

/**
 * This activity is based on the Public API for the Bluetooth GATT Profile.
 * The BLE-GATT-Movesense-2.0 application provided by anderslm on github:
 * https://gits-15.sys.kth.se/anderslm/Ble-Gatt-Movesense-2.0 is used as a reference.
 * The activity subscribes to writeable, readable, indicatable and
 * notifiable characteristics of a fitness machine service following the
 * FTMS protocol for bluetooth fitness machines:
 * https://www.bluetooth.org/DocMan/handlers/DownloadDoc.ashx?doc_id=423422
 * CSV Reader and Writer are implemented from the FastCSV (v1.0.2) API  provided by Oliver Siegmar
 * https://github.com/osiegmar/FastCSV
 **/
public class RunActivity extends MenuActivity {

    private BluetoothDevice mSelectedFTMS = null;
    private BluetoothGatt mBluetoothGatt = null;

    private Handler mHandler;

    //
    public static final String EXTRAS_HR_NAME = "HR_NAME";
    public static final String EXTRAS_HR_ADDRESS = "HR_ADDRESS";
    public static final String EXTRAS_FTMS_ADDRESS = "FTMS_ADDRESS";

    private TextView mHRView;

    private String mHrAddress;
    private String mFtmsAddress;

    private BleHeartRateService mBluetoothLeHrService;
    private BleFtmsService mBluetoothLeFtmsService;
    //

    private TextView mConnectionView;
    private TextView mTdView;
    private TextView mSpeedView;
    private TextView mInclView;
    private TextView mControlSpeedView;
    private TextView mControlInclView;
    private SeekBar mSpeedBar;
    private SeekBar mInclBar;
    private ImageButton mSpeedUp;
    private ImageButton mSpeedDown;
    private ImageButton mInclUp;
    private ImageButton mInclDown;
    private Button StartStopButton;

    private boolean isRunning;
    private boolean isConnected;

    private static int maxSpeed;
    private static int minSpeed;
    private static double speedIncrement;
    private static int maxIncl;
    private static int minIncl;
    private static double inclIncrement;

    private static final String LOG_TAG = "RunActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout contentFrameLayout = findViewById(R.id.menu_frame);
        getLayoutInflater().inflate(R.layout.activity_run, contentFrameLayout);

        //Find views, buttons and seekbars
        TextView mDeviceView = findViewById(R.id.deviceView);
        mConnectionView = findViewById(R.id.connection_view);
        mTdView = findViewById(R.id.Td_view);
        mSpeedView = findViewById(R.id.speed_view);
        mInclView = findViewById(R.id.incl_view);
        mHRView = findViewById(R.id.heart_rate_view);
        mSpeedBar = findViewById(R.id.speed_bar);
        mInclBar = findViewById(R.id.incl_bar);
        mSpeedUp = findViewById(R.id.speed_up);
        mSpeedDown = findViewById(R.id.speed_down);
        mInclUp = findViewById(R.id.incl_up);
        mInclDown = findViewById(R.id.incl_down);
        mControlSpeedView = findViewById(R.id.speed);
        mControlInclView = findViewById(R.id.incl);
        StartStopButton = findViewById(R.id.startStopButton);
        //Toolbar myToolbar = findViewById(R.id.my_toolbar);
        //setSupportActionBar(myToolbar);
        isConnected = false;
        //Disable all buttons until connected
        enable(false);

        // Get the selected device from the intent
        Intent intent = getIntent();
        mSelectedFTMS = intent.getParcelableExtra(StartTrainingActivity.SELECTED_FTMS);
        Log.i(LOG_TAG, "selected device" + mSelectedFTMS);
        if (mSelectedFTMS == null) {
            mDeviceView.setText(R.string.devices_info);
        } else {
            mDeviceView.setText(mSelectedFTMS.getName());
        }

        //The bluetooth callbacks are performed on a worker thread.
        //Use this handler to update ui.
        mHandler = new Handler();

        //SeekbarListeners. Update text views in accordance to changes
        mSpeedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                String controlSpeed = String.format(Locale.ENGLISH, "%.1f km/h", (float) (progress * speedIncrement));
                mControlSpeedView.setText(controlSpeed);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }


            //When seekbar released, let treadmill know.
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar.getProgress() == 0)
                    seekBar.setProgress(1);
                setSpeed((float) (seekBar.getProgress() * speedIncrement));
            }
        });

        mInclBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                // Print current value (seekbar progress*increment) to text view
                String controlIncl = String.format(Locale.ENGLISH, "%.1f", (float) (progress * inclIncrement)) + " %";
                mControlInclView.setText(controlIncl);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Set inclination with seekbar change
                setIncl((float) (seekBar.getProgress() * inclIncrement));
            }
        });

        // the intent from BleHeartRateService, that started this activity
        mHrAddress = intent.getStringExtra(EXTRAS_HR_ADDRESS);
        if (mHrAddress == null) {
            mHRView.setText(R.string.no_data);
        } else {
            mHRView.setText(R.string.connecting_hr);
        }

        // the intent from BleHeartRateService, that started this activity
        mFtmsAddress = intent.getStringExtra(EXTRAS_FTMS_ADDRESS);


        // NB! bind to the BleHeartRateService
        // Use onResume or onStart to register a BroadcastReceiver.
        Intent gattHRServiceIntent = new Intent(this, BleHeartRateService.class);
        bindService(gattHRServiceIntent, mHrServiceConnection, BIND_AUTO_CREATE);

        // NB! bind to the BleFtmsService
        // Use onResume or onStart to register a BroadcastReceiver.
        Intent gattFtmsServiceIntent = new Intent(this, BleFtmsService.class);
        bindService(gattFtmsServiceIntent, mFtmsServiceConnection, BIND_AUTO_CREATE);

    }

   /* @Override
    protected void onStart() {
        super.onStart();
        if (mSelectedDevice != null && mBluetoothGatt == null) {
            mHandler.postDelayed(() -> {
                // Connect and register call backs for bluetooth gatt
                mBluetoothGatt =
                        mSelectedDevice.connectGatt(RunActivity.this, false, mBtGattCallback);
            }, 500);
        }
    }
*/
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeHrService != null) {
            final boolean resultHr = mBluetoothLeHrService.connect(mHrAddress);
        }
        if (mBluetoothLeFtmsService != null) {
            final boolean resultFtms = mBluetoothLeFtmsService.connect(mFtmsAddress);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO: Find other way to change the text color
        getMenuInflater().inflate(R.menu.menu_run, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.connect_hr_sensor) {
            Intent intentNew = new Intent(RunActivity.this, ScanHRActivity.class);
            intentNew.putExtra(StartTrainingActivity.SELECTED_FTMS,mSelectedFTMS);
            startActivity(intentNew);
        } else if (item.getItemId() == R.id.disconnect) {
            if (mBluetoothGatt != null) {
                mBluetoothGatt.disconnect();
                mConnectionView.setText(R.string.disconnected);
                enable(false);
                try {
                    mBluetoothGatt.close();
                } catch (Exception e) {
                    // Android BLE API bug handling
                }
            }
        }else if (!isConnected)
            return false;
        else if (item.getItemId() == R.id.load_workout) {
            Intent intentLoad = new Intent(Intent.ACTION_GET_CONTENT);
            intentLoad.setType("*/*");
            startActivityForResult(intentLoad, requestCode);
        } else if (item.getItemId() == R.id.new_workout) {
            Intent intentNew = new Intent(RunActivity.this, WorkoutActivity.class);
            startActivity(intentNew);
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Callbacks to/from the treadmill service. The callbacks are executed on a worker thread.
     * Therefore, a Handler is used to update ui. Most common root of error is that an operation
     * has not finished when the net one is read. The new one will then not be executed. To prevent
     * this, a delay of 500 ms is put on the callbacks; onPostDelay.
     */
 /*   private final BluetoothGattCallback mBtGattCallback = new BluetoothGattCallback() {

        final List<BluetoothGattCharacteristic> chars = new ArrayList<>();

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            System.out.println(status);
            System.out.println(newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                // Discover services
                mHandler.postDelayed(() -> {
                    mBluetoothGatt = gatt;
                    gatt.discoverServices();
                }, 500);
            } else if (status == 133 || status == 8) {
                //Unexplained error 133, is not described in documentation.
                //To get past it we only need to try again.
                //Error 8 is a timeout error. We manage to connect but, this error shows up
                //afterwards. To get past it we also just need to try again. Can take a while.
                try {
                    mBluetoothGatt.close();
                } catch (Exception e) {
                }
                mHandler.postDelayed(() -> {
                    mBluetoothGatt =
                            mSelectedDevice.connectGatt(RunActivity.this, false, mBtGattCallback, BluetoothDevice.TRANSPORT_LE);
                    mConnectionView.setText(R.string.connecting);
                }, 500);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Close connection and display info in ui
                mBluetoothGatt = null;
                mHandler.post(() -> mConnectionView.setText(R.string.disconnected));
            }
        }

        //Service and its characteristics discovered. Subscribe to what interested.
        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Debug: list discovered services
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    Log.i(LOG_TAG, service.getUuid().toString());
                }
                mHandler.post(() -> mConnectionView.setText(R.string.connected));

                isConnected = true;
                // Get the ftms service
                BluetoothGattService ftmsService = gatt.getService(FTMS_SERVICE);
                if (ftmsService != null) {
                    // debug: service present, list characteristics
                    List<BluetoothGattCharacteristic> characteristics =
                            ftmsService.getCharacteristics();
                    for (BluetoothGattCharacteristic chara : characteristics) {
                        Log.i(LOG_TAG, chara.getUuid().toString());
                    }
                    //Find the control characteristic
                    commandChar = ftmsService.getCharacteristic(TREADMILL_CONTROL_CHARACTERISTIC);
                    BluetoothGattCharacteristic dataCharacteristic = ftmsService.getCharacteristic(TREADMILL_DATA_CHARACTERISTIC);
                    //Find characteristics for status, supported speed and inclination
                    BluetoothGattCharacteristic speeds =
                            ftmsService.getCharacteristic(SUPPORTED_SPEED_CHARACTERISTIC);
                    BluetoothGattCharacteristic inclinations =
                            ftmsService.getCharacteristic(SUPPORTED_INCLINATION_CHARACTERISTIC);
                    BluetoothGattCharacteristic statusChar =
                            ftmsService.getCharacteristic(FITNESS_MACHINE_STATUS_CHARACTERISTIC);
                    //Add characteristics to a list, to prevent timeouts. Used in requestCharacteristics method.
                    chars.add(speeds);
                    chars.add(inclinations);
                    chars.add(dataCharacteristic);
                    chars.add(statusChar);
                    chars.add(commandChar);

                    mHandler.postDelayed(() -> requestCharacteristics(gatt), 500);

                } else {
                    mHandler.post(() -> MsgUtils.showAlert("Alert!",
                            getString(R.string.service_not_found),
                            RunActivity.this));
                }
            }
        }

        //Assign right methods to different characteristics.
        public void requestCharacteristics(BluetoothGatt gatt) {
            if ((chars.get(0).getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0)
                gatt.readCharacteristic(chars.get(0));
            else if ((chars.get(0).getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
                notifyCharacteristic(gatt, chars.get(0));
            else if ((chars.get(0).getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)
                indicateCharacteristic(gatt, chars.get(0));

        }


        //Receive and parse notifiable and indicatable data from the treadmill.
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic) {
            Log.i(LOG_TAG, "onCharacteristicChanged " + characteristic.getUuid());

            if (TREADMILL_DATA_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();

                double instSpeed = TypeConverter.BytesToUInt(data, 2, 2) / 100.0;
                int totDist = TypeConverter.BytesToUInt(data, 6, 3);
                double incl = TypeConverter.BytesToSInt(data, 9, 2) / 10.0;
                System.out.println("Instantaneous speed: " + instSpeed);

                mHandler.post(() -> {
                    mTdView.setText(String.valueOf(totDist));
                    mSpeedView.setText(String.valueOf(instSpeed));
                    mInclView.setText(String.valueOf(incl));
                });
            } else if (TREADMILL_CONTROL_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data[1] == 0 && data[2] == 1) {
                    System.out.println("Control permission granted");
                    //Enable control buttons
                    mHandler.post(() -> {
                        enable(true);
                        //Start treadmill
                        byte[] start = {7};
                        commandChar.setValue(start);
                        boolean wasSuccess = mBluetoothGatt.writeCharacteristic(commandChar);
                        Log.d("writeCharacteristic", "Treadmill running state: " + wasSuccess);
                    });
                } else if (data[2] == 1) {
                    if (data[1] == 2)
                        System.out.println("Speed control point successful");
                    else if (data[1] == 3)
                        System.out.println("Inclination control point successful");
                    else if (data[1] == 7) {
                        System.out.println("Treadmill successfully started");
                        mHandler.post(() -> {
                            StartStopButton.setBackgroundColor(Color.RED);
                            StartStopButton.setText(R.string.stop);
                            isRunning = true;
                        });
                    } else if (data[1] == 8) {
                        System.out.println("Treadmill successfully stopped");
                        mHandler.post(() -> {
                            StartStopButton.setBackgroundColor(Color.GREEN);
                            StartStopButton.setText(R.string.start);
                            isRunning = false;
                        });
                    }
                } else if (data[2] == 3)
                    System.out.println("Control point out of range");
                else
                    System.out.println("Control operation failed");
            } else if (FITNESS_MACHINE_STATUS_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();

                if (data[0] == 4) {
                    System.out.println("Treadmill started by user");
                    mHandler.post(() -> {
                        StartStopButton.setBackgroundColor(Color.RED);
                        StartStopButton.setText(R.string.stop);
                        isRunning = true;
                    });
                } else if (data[0] == 2 && data[1] == 1) {
                    System.out.println("Treadmill stopped by user");
                    mHandler.post(() -> {
                        StartStopButton.setBackgroundColor(Color.GREEN);
                        StartStopButton.setText(R.string.start);
                        isRunning = false;
                    });
                } else if (data[0] == 5) {
                    System.out.println("Speed changed");
                    int setSpeed = TypeConverter.BytesToUInt(data, 1, 2);
                    String speedText = String.format(Locale.ENGLISH, "%.1f km/h", setSpeed / 100.0);
                    mHandler.post(() -> {
                        mControlSpeedView.setText(speedText);
                        mSpeedBar.setProgress((int) (setSpeed * speedIncrement));
                    });
                } else if (data[0] == 6) {
                    System.out.println("Inclination changed");
                    int setIncl = TypeConverter.BytesToSInt(data, 1, 2);
                    String inclText = String.format(Locale.ENGLISH, "%.1f", setIncl / 10.0) + " %";
                    mHandler.post(() -> {
                        mControlInclView.setText(inclText);
                        mInclBar.setProgress((int) (setIncl / (inclIncrement * 10)));
                    });
                }
            }
        }

        //Performed on readable characteristics
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            Log.i(LOG_TAG, "onCharacteristicRead " + characteristic.getUuid().toString());
            if (SUPPORTED_SPEED_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] speeds = characteristic.getValue();

                minSpeed = TypeConverter.BytesToUInt(speeds, 0, 2);
                maxSpeed = TypeConverter.BytesToUInt(speeds, 2, 2);
                speedIncrement = TypeConverter.BytesToUInt(speeds, 4, 2) / 100.0;
                System.out.println("Min speed: " + minSpeed / 100.0);
                System.out.println("Max speed: " + maxSpeed / 100.0);
                System.out.println("Min speed increment: " + speedIncrement);
                // Set seekbar attributes
                mSpeedBar.setMax((int) (maxSpeed * speedIncrement));
            }
            if (SUPPORTED_INCLINATION_CHARACTERISTIC.equals(characteristic.getUuid())) {
                byte[] inclinations = characteristic.getValue();

                minIncl = TypeConverter.BytesToSInt(inclinations, 0, 2);
                maxIncl = TypeConverter.BytesToSInt(inclinations, 2, 2);
                inclIncrement = TypeConverter.BytesToSInt(inclinations, 4, 2) / 10.0;
                System.out.println("Min incl: " + minIncl / 10.0);
                System.out.println("Max incl: " + maxIncl / 10.0);
                System.out.println("Min incl increment: " + inclIncrement);
                // Set seekbar attributes
                int inclIntervals = (int) (maxIncl / (10 * inclIncrement));
                mInclBar.setMax(inclIntervals);
            }

            chars.remove(chars.get(0));

            if (chars.size() > 0) {
                mHandler.postDelayed(() -> requestCharacteristics(gatt), 500);
            }
        }

        //Performed on notifiable characteristics. Timeout handling
        public void notifyCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            boolean success = gatt.setCharacteristicNotification(characteristic, true);
            if (success) {
                Log.i(LOG_TAG, "setCharactNotification success");
                //If success, subscribe to the notifications of the characteristic.
                BluetoothGattDescriptor descriptor =
                        characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            } else {
                Log.i(LOG_TAG, "setCharacteristicNotification failed");
            }

            chars.remove(chars.get(0));

            if (chars.size() > 0) {
                mHandler.postDelayed(() -> requestCharacteristics(gatt), 500);
            }
        }

        //Performed on indicatable characteristics. Timeout handling
        public void indicateCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            boolean success = gatt.setCharacteristicNotification(characteristic, true);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            if (success) {
                Log.i(LOG_TAG, "setCharactNotification success");
                //If success, subscribe to the notifications of the characteristic.
                BluetoothGattDescriptor descriptor =
                        characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            } else {
                Log.i(LOG_TAG, "setCharacteristicNotification failed");
            }

            chars.remove(chars.get(0));

            mHandler.postDelayed(() -> {
                if (TREADMILL_CONTROL_CHARACTERISTIC.equals(characteristic.getUuid())) {   //ask for control permission
                    byte[] permission = {0};
                    commandChar.setValue(permission);
                    boolean wasSuccess = mBluetoothGatt.writeCharacteristic(commandChar);
                    Log.d("control permission", "Success: " + wasSuccess);
                }
                if (chars.size() > 0) {
                    requestCharacteristics(gatt);
                }
            }, 500);
        }

    };
*/
    //Reading a pre-defined workout from a csv
    int requestCode = 1;

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            final String[] split = uri.getPath().split(":"); //split the path
            String filePath = split[1]; //assign second part to a string
            File file = new File(filePath);
            CsvReader csvReader = new CsvReader();
            csvReader.setContainsHeader(true); //If csv contains headers
            ArrayList<WorkoutEntry> workout = new ArrayList<>();
            //CSV parsing
            try (CsvParser csvParser = csvReader.parse(file, StandardCharsets.UTF_8)) {
                CsvRow row;
                while ((row = csvParser.nextRow()) != null) {
                    WorkoutEntry newEntry = new WorkoutEntry();
                    newEntry.setDur(Float.parseFloat(row.getField("Duration")));
                    newEntry.setSpeed(Float.parseFloat(row.getField("Speed")));
                    newEntry.setIncl(Float.parseFloat(row.getField("Inclination")));
                    workout.add(newEntry);
                }
                Toast.makeText(this, "Loaded from" + filePath, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                e.printStackTrace();
            }

            ListIterator<WorkoutEntry> workoutIterate = workout.listIterator();
            //This runnable is designed to prevent timeout errors. The speed is changed just before the inclination
            Runnable executeCsv = new Runnable() {
                int counter = 0;
                WorkoutEntry nextEntry;

                @Override
                public void run() {
                    if (workoutIterate.hasNext() && counter % 2 == 0) {
                        nextEntry = workoutIterate.next();
                        setSpeed(nextEntry.getSpeed());
                        mHandler.postDelayed(this, 500);
                        counter++;
                    } else if (counter % 2 == 1) {
                        setIncl(nextEntry.getIncl());
                        mHandler.postDelayed(this, (long) (nextEntry.getDur() * 60 * 1000 - 500));
                        counter++;
                    }
                }
            };
            mHandler.post(executeCsv);
        }
    }

    //Method linked to green speed arrow
    public void speed_up(View view) {
        int curr = mSpeedBar.getProgress();
        mSpeedBar.setProgress(curr + 1);
        setSpeed((float) (mSpeedBar.getProgress() * speedIncrement));
    }//Method linked to red speed arrow

    public void speed_down(View view) {
        int curr = mSpeedBar.getProgress();
        if (curr != 1) {
            mSpeedBar.setProgress(curr - 1);
            setSpeed((float) (mSpeedBar.getProgress() * speedIncrement));
        }
    }//Method linked to green inclination arrow.

    public void incl_up(View view) {
        int curr = mInclBar.getProgress();
        mInclBar.setProgress(curr + 1);
        setIncl((float) (mInclBar.getProgress() * inclIncrement));
    }//Method linked to red inclination arrow.

    public void incl_down(View view) {
        int curr = mInclBar.getProgress();
        mInclBar.setProgress(curr - 1);
        setIncl((float) (mInclBar.getProgress() * inclIncrement));
    }

    //Method to change speed of treadmill. Will have to be accepted before execution. Happens in onChangedCharacteristic
    public void setSpeed(float Speed) {
        byte[] a = new byte[3];
        a[0] = 2;
        byte[] b = TypeConverter.intToBytes((int) (Speed * 100), 2);
        System.arraycopy(b, 0, a, 1, 2);
        broadcastControl(a);
        /*
        commandChar.setValue(a);
        boolean wasSuccess = mBluetoothGatt.writeCharacteristic(commandChar);
        Log.d("writeCharacteristic", "Write speed Success: " + wasSuccess);
         */
    }

    //Method to change inclination of treadmill. Will have to be accepted before execution. Happens in onChangedCharacteristic
    public void setIncl(float Inclination) {
        byte[] a = new byte[3];
        a[0] = 3;
        byte[] b = TypeConverter.intToBytes((int) (Inclination * 10), 2);
        System.arraycopy(b, 0, a, 1, 2);
        broadcastControl(a);
        /*
        commandChar.setValue(a);
        boolean wasSuccess = mBluetoothGatt.writeCharacteristic(commandChar);
        Log.d("writeCharacteristic", "Write speed Success: " + wasSuccess);
         */
    }

    //Singletons used in setting spinner values
    public static double getSpeedIncrement() {
        return speedIncrement;
    }

    public static double getInclIncrement() {
        return inclIncrement;
    }

    public static int getMaxSpeed() {
        return maxSpeed;
    }

    public static int getMaxIncl() {
        return maxIncl;
    }

    //Method linked to the start/stop button
    public void start_stop(View view) {
        byte[] a = new byte[1];
        if (isRunning)
            a[0] = 8;
        else
            a[0] = 7;
        broadcastControl(a);
        /*
        commandChar.setValue(a);
        boolean wasSuccess = mBluetoothGatt.writeCharacteristic(commandChar);
        Log.d("writeCharacteristic", "Treadmill running state: " + wasSuccess);
         */
    }

    //Method to disable and enable buttons and seekbars on ui
    public void enable(Boolean enable) {
        mSpeedUp.setEnabled(enable);
        mSpeedDown.setEnabled(enable);
        mInclUp.setEnabled(enable);
        mInclDown.setEnabled(enable);
        mSpeedBar.setEnabled(enable);
        mInclBar.setEnabled(enable);
        StartStopButton.setEnabled(enable);

        float alpha;
        if (enable)
            alpha = 1.0f;
        else
            alpha = 0.1f;
        mSpeedUp.setAlpha(alpha);
        mSpeedDown.setAlpha(alpha);
        mInclUp.setAlpha(alpha);
        mInclDown.setAlpha(alpha);
    }

    /*
    Callback methods to manage the (BleHeartRate)Service lifecycle.
    */
    private final ServiceConnection mHrServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeHrService = ((BleHeartRateService.LocalBinder) service).getService();
            if (!mBluetoothLeHrService.initialize()) {
                Log.i(LOG_TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeHrService.connect(mHrAddress);
            Log.i(LOG_TAG, "Hr onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeHrService = null;
            Log.i(LOG_TAG, "onServiceDisconnected");
        }
    };

    /*
    Callback methods to manage the (Ftms)Service lifecycle.
    */
    private final ServiceConnection mFtmsServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeFtmsService = ((BleFtmsService.LocalBinder) service).getService();
            if (!mBluetoothLeFtmsService.initialize()) {
                Log.i(LOG_TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeFtmsService.connect(mFtmsAddress);
            Log.i(LOG_TAG, "Ftms onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeFtmsService = null;
            Log.i(LOG_TAG, "onServiceDisconnected");
        }
    };


    private void broadcastControl(byte[] command_char) {
        final Intent intent = new Intent(ACTION_GATT_FTMS_EVENTS);
        intent.putExtra(FTMS_EVENT, GattActions.FTMS_Event.FTMS_COMMAND);
        //Add control updates to intent
        intent.putExtra(COMMAND_CHAR, command_char);
        sendBroadcast(intent);
    }


    /*
    A BroadcastReceiver handling various events fired by the Service, see GattActions.Event.
    */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ACTION_GATT_HEART_RATE_EVENTS.equals(action)) {
                GattActions.HR_Event hr_event = (GattActions.HR_Event) intent.getSerializableExtra(HR_EVENT);
                if (hr_event != null) {
                    switch (hr_event) {
                        case GATT_CONNECTED:
                        case GATT_DISCONNECTED:
                        case GATT_CONNECTING:
                            mHRView.setText("-");
                        case GATT_SERVICES_DISCOVERED:
                        case HEART_RATE_SERVICE_DISCOVERED:
                            mHRView.setText("-");
                            break;
                        case HR_DATA_AVAILABLE:
                            int heartRate = intent.getIntExtra(HEART_RATE_DATA, -1);
                            Log.i(LOG_TAG, "got hr data: " + heartRate);
                            if (heartRate < 0) {
                                mHRView.setText("?");
                            } else {
                                mHRView.setText(String.format("%d", heartRate));
                            }
                            break;
                        case HEART_RATE_SERVICE_NOT_AVAILABLE:
                            break;
                        default:
                            mHRView.setText("?");
                    }
                }

            }
            if (ACTION_GATT_FTMS_EVENTS.equals(action)) {
                GattActions.FTMS_Event ftms_event = (GattActions.FTMS_Event) intent.getSerializableExtra(FTMS_EVENT);
                if (ftms_event != null) {
                    switch (ftms_event) {
                        case GATT_CONNECTED:
                            //Does not work, find out why
                            mConnectionView.setText(R.string.connected);
                        case GATT_DISCONNECTED:
                            mConnectionView.setText(R.string.disconnected);
                        case GATT_CONNECTING:
                            mConnectionView.setText(R.string.connecting);
                        case GATT_SERVICES_DISCOVERED:
                            // TODO: Update for FTMS
                        case FTMS_DATA_AVAILABLE:
                            double instSpeed = intent.getDoubleExtra(INSTANT_SPEED_DATA, -1);
                            double incl = intent.getDoubleExtra(INCLINATION_DATA, -1);
                            int totDist = intent.getIntExtra(TOTAL_DISTANCE_DATA, -1);

                            if(instSpeed != -1 ) {
                                Log.i(LOG_TAG, "got ftms data: ");
                                Log.i(LOG_TAG, "Speed: " + instSpeed);
                                Log.i(LOG_TAG, "Inclination: " + incl);
                                Log.i(LOG_TAG, "Distance: " + totDist);

                                mTdView.setText(String.valueOf(totDist));
                                //The following two views are not displayed, this will not be needed!
                                mSpeedView.setText(String.valueOf(instSpeed));
                                mInclView.setText(String.valueOf(incl));
                            }

                            break;
                        case SUPPORTED_SPEED:
                            maxSpeed = intent.getIntExtra(MAX_SPEED,0);
                            minSpeed = intent.getIntExtra(MIN_SPEED,0);
                            speedIncrement = intent.getDoubleExtra(SPEED_INCREMENT,0);

                            mSpeedBar.setMax((int) (maxSpeed * speedIncrement));
                            break;
                        case SUPPORTED_INCLINATION:
                            maxIncl = intent.getIntExtra(MAX_INCL,0);
                            minIncl = intent.getIntExtra(MIN_INCL,0);
                            inclIncrement = intent.getDoubleExtra(INCL_INCREMENT,0);

                            int inclIntervals = (int) (maxIncl / (10 * inclIncrement));
                            mInclBar.setMax(inclIntervals);
                            break;
                        case FTMS_STATUS:
                            int type = intent.getIntExtra(STATUS_TYPE,-1);
                            int value = intent.getIntExtra(STATUS_VALUE,0);
                            if (type == 4) {
                                System.out.println("Treadmill started by user");
                                StartStopButton.setBackgroundColor(Color.RED);
                                StartStopButton.setText(R.string.stop);
                                isRunning = true;
                            } else if (type == 2 && value == 1) {
                                System.out.println("Treadmill stopped by user");
                                StartStopButton.setBackgroundColor(Color.GREEN);
                                StartStopButton.setText(R.string.start);
                                isRunning = false;
                            } else if (type == 5) {
                                System.out.println("Speed changed");
                                String speedText = String.format(Locale.ENGLISH, "%.1f km/h", value / 100.0);
                                mControlSpeedView.setText(speedText);
                                mSpeedBar.setProgress((int) (value * speedIncrement));
                            } else if (type == 6) {
                                System.out.println("Inclination changed");
                                String inclText = String.format(Locale.ENGLISH, "%.1f", value / 10.0) + " %";
                                mControlInclView.setText(inclText);
                                mInclBar.setProgress((int) (value / (inclIncrement * 10)));
                            }

                        case FTMS_CONTROL:
                            String control_type = intent.getStringExtra(CONTROL_TYPE);
                            switch (control_type) {
                                case "Control granted":
                                    enable(true);
                                    break;
                                case "Start":
                                    StartStopButton.setBackgroundColor(Color.RED);
                                    StartStopButton.setText(R.string.stop);
                                    isRunning = true;
                                    break;
                                case "Stop":
                                    StartStopButton.setBackgroundColor(Color.GREEN);
                                    StartStopButton.setText(R.string.start);
                                    isRunning = false;
                                    break;
                            }


                    }
                }
            }
        }
    };

    // Intent filter for broadcast updates from BleHeartRateServices and BleFtmsServices
    private IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_HEART_RATE_EVENTS);
        intentFilter.addAction(ACTION_GATT_FTMS_EVENTS);
        return intentFilter;
    }

}