package com.android.djavid.squats;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private enum ActivityEnum {
        STANDING, SQUATING, DEFAULT;
    }

    private int n;

    private int dataCount;
    private boolean started;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private float[] accelerometer;  //данные с акселерометра
    private float[][] testData;
    private double[] prediction;

    private int squats_number;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE); // Получаем менеджер сенсоров
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        n = 100;
        accelerometer = new float[3];
        testData = new float[n][];
        dataCount = 0;
        squats_number = 0;
        started = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        getSensorData(sensorEvent);

        if (started) {
            testData[dataCount] = new float[3];
            System.arraycopy(accelerometer, 0, testData[dataCount], 0, 3);

            dataCount += 1;
            if (dataCount == n) {
                DataLine dataLine = new DataLine(testData);
                ActivityEnum result = predictData(dataLine);

                if (result == ActivityEnum.SQUATING) {
                    squats_number += 1;
                }

                String str = getResources().getString(R.string.squats_number);
                str = String.format(str,squats_number);
                ((TextView) findViewById(R.id.textview_squats_number)).setText(str);

                testData = new float[n][];
                dataCount = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    protected void onResume(){
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    public void resetSquats(View view) {
        squats_number = 0;

        String str = getResources().getString(R.string.squats_number);
        str = String.format(str,squats_number);
        ((TextView) findViewById(R.id.textview_squats_number)).setText(str);
    }

    public void toggleRecognising(View view) {
        final Button button = (Button) findViewById(R.id.button_toggle);
        final TextView timer = (TextView) findViewById(R.id.textview_timer);
        final TextView result = (TextView) findViewById(R.id.textview_squats_number);

        if(!started) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage("Пожалуйста положите телефон в карман для более точного распознавания!")
                    .setCancelable(false)
                    .setPositiveButton("Ok",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();

                                    String str = getResources().getString(R.string.waiting_activity);
                                    result.setText(str);
                                    timer.setVisibility(View.VISIBLE);
                                    button.setVisibility(View.INVISIBLE);

                                    new CountDownTimer(3000, 1000) {
                                        public void onTick(long millisUntilFinished) {
                                            String str = getResources().getString(R.string.value_prediction_timer);
                                            str = String.format(str, millisUntilFinished / 1000);
                                            timer.setText(str);
                                        }

                                        public void onFinish() {
                                            button.setText(R.string.button_stop_recognising);
                                            button.setVisibility(View.VISIBLE);
                                            timer.setText("");
                                            started = true;

                                            String str = getResources().getString(R.string.squats_number);
                                            str = String.format(str,squats_number);
                                            ((TextView) findViewById(R.id.textview_squats_number)).setText(str);
                                        }
                                    }.start();
                                }
                            });
            AlertDialog alert = builder.create();
            alert.show();
        } else {
            started = false;
            testData = new float[n][];
            dataCount = 0;;
            button.setText(R.string.button_start_recognising);

            //result.setVisibility(View.INVISIBLE);
        }
    }

    private void getSensorData(SensorEvent event) {
        final int type = event.sensor.getType();

        if (type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometer, 0, 3);
        }
    }

    private ActivityEnum predictData(DataLine data) {
        float[] calcData = data.calcMeanStd();
        return LogisticFunc(calcData);
    }

    private ActivityEnum LogisticFunc(float[] x) {
        double[] squating_coefs = {0.74829685,  0.17141862, -0.10667058,  1.66918973,  0.83665934, 0.91311457};
        prediction = new double[2];

        double z = 0;
        for (int i = 0; i < squating_coefs.length; i++) {
            z += squating_coefs[i] * x[i];
        }

        prediction[0] = 1.0 / (1.0 + Math.exp(-z));
        prediction[1] = 1.0 - prediction[0];

        ActivityEnum activity;
        if (prediction[0] < prediction[1]) {
            activity = ActivityEnum.STANDING;
        } else {
            activity = ActivityEnum.SQUATING;
        }

        return activity;
    }


    private class DataLine {
        private float[][] testData;

        public DataLine(float[][] arr) {
            testData = new float[arr.length][];
            System.arraycopy(arr, 0, testData, 0, arr.length);
        }

        public float[] calcMeanStd() {
            float[] calcData = new float[testData[0].length * 2];
            int j = 0;
            for (int i = 0; i < calcData.length; i += 2) {
                calcData[i] = calcMean(j);
                j += 1;
            }

            j = 0;
            for (int i = 1; i < calcData.length; i += 2) {
                calcData[i] = calcStd(j);
                j += 1;
            }

            return calcData;
        }

        private float calcMean(int col) {
            float mean = 0;
            for (float[] item : testData) {
                mean += item[col];
            }

            return  mean / (float)testData.length;
        }

        private float calcStd(int col) {
            float mean = calcMean(col);
            float sum = 0;

            for (float[] item : testData) {
                sum += Math.pow(item[col] - mean, 2);
            }
            sum /= (float)testData.length;

            return (float)Math.sqrt(sum);
        }
    }
}
