package com.ragnarok.rxcamera.example;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ragnarok.rxcamera.RxCamera;
import com.ragnarok.rxcamera.RxCameraData;
import com.ragnarok.rxcamera.config.CameraUtil;
import com.ragnarok.rxcamera.config.RxCameraConfig;
import com.ragnarok.rxcamera.config.RxCameraConfigChooser;
import com.ragnarok.rxcamera.request.Func;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Example.MainActivity";

    private TextureView textureView;
    private Button openCameraBtn;
    private Button closeCameraBtn;
    private TextView logTextView;
    private ScrollView logArea;

    private RxCamera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textureView = (TextureView) findViewById(R.id.preview_surface);
        openCameraBtn = (Button) findViewById(R.id.open_camera);
        closeCameraBtn = (Button) findViewById(R.id.close_camera);
        logTextView = (TextView) findViewById(R.id.log_textview);
        logArea = (ScrollView) findViewById(R.id.log_area);

        openCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });

        closeCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null) {
                    camera.closeCameraWithResult().subscribe(new Action1<Boolean>() {
                        @Override
                        public void call(Boolean aBoolean) {
                            showLog("close camera finished, success: " + aBoolean);
                        }
                    });
                }
            }
        });

        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!checkCamera()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    final float x = event.getX();
                    final float y = event.getY();
                    final Rect rect = CameraUtil.transferCameraAreaFromOuterSize(new Point((int)x, (int)y),
                            new Point(textureView.getWidth(), textureView.getHeight()), 100);
                    List<Camera.Area> areaList = Collections.singletonList(new Camera.Area(rect, 1000));
                    Observable.zip(camera.action().areaFocusAction(areaList),
                            camera.action().areaMeterAction(areaList),
                            new Func2<RxCamera, RxCamera, Object>() {
                                @Override
                                public Object call(RxCamera rxCamera, RxCamera rxCamera2) {
                                    return rxCamera;
                                }
                            }).subscribe(new Subscriber<Object>() {
                        @Override
                        public void onCompleted() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            showLog("area focus and metering failed: " + e.getMessage());
                        }

                        @Override
                        public void onNext(Object o) {
                            showLog(String.format("area focus and metering success, x: %s, y: %s, area: %s", x, y, rect.toShortString()));
                        }
                    });
                }
                return false;
            }
        });

    }


    private void openCamera() {
        RxCameraConfig config = RxCameraConfigChooser.obtain().
                useBackCamera().
                setAutoFocus(true).
                setPreferPreviewFrameRate(15, 30).
                setPreferPreviewSize(new Point(640, 480)).
                setHandleSurfaceEvent(true).
                get();
        Log.d(TAG, "config: " + config);
        RxCamera.open(this, config).flatMap(new Func1<RxCamera, Observable<RxCamera>>() {
            @Override
            public Observable<RxCamera> call(RxCamera rxCamera) {
                showLog("isopen: " + rxCamera.isOpenCamera() + ", thread: " + Thread.currentThread());
                camera = rxCamera;
                return rxCamera.bindTexture(textureView);
            }
        }).flatMap(new Func1<RxCamera, Observable<RxCamera>>() {
            @Override
            public Observable<RxCamera> call(RxCamera rxCamera) {
                showLog("isbindsurface: " + rxCamera.isBindSurface() + ", thread: " + Thread.currentThread());
                return rxCamera.startPreview();
            }
        }).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<RxCamera>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("open camera error: " + e.getMessage());
            }

            @Override
            public void onNext(final RxCamera rxCamera) {
                camera = rxCamera;
                showLog("open camera success: " + camera);
                Toast.makeText(MainActivity.this, "Now you can tap to focus", Toast.LENGTH_LONG).show();
            }
        });


    }

    private void showLog(String s) {
        Log.d(TAG, s);
        logTextView.append(s + "\n");
        logTextView.post(new Runnable() {
            @Override
            public void run() {
                logArea.fullScroll(View.FOCUS_DOWN);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.closeCamera();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_log:
                toggleLogArea();
                break;
            case R.id.action_successive_data:
                requestSuccessiveData();
                break;
            case R.id.action_periodic_data:
                requestPeriodicData();
                break;
            case R.id.action_one_shot:
                requestOneShot();
                break;
            case R.id.action_take_picture:
                requestTakePicture();
                break;
            case R.id.action_zoom:
                actionZoom();
                break;
            case R.id.action_smooth_zoom:
                actionSmoothZoom();
                break;
            case R.id.action_open_flash:
                actionOpenFlash();
                break;
            case R.id.action_close_flash:
                actionCloseFlash();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleLogArea() {
        if (logArea.getVisibility() == View.VISIBLE) {
            logArea.setVisibility(View.GONE);
        } else {
            logArea.setVisibility(View.VISIBLE);
        }
    }

    private void requestSuccessiveData() {
        if (!checkCamera()) {
            return;
        }
        camera.request().successiveDataRequest().subscribe(new Action1<RxCameraData>() {
            @Override
            public void call(RxCameraData rxCameraData) {
                showLog("successiveData, cameraData.length: " + rxCameraData.cameraData.length);
            }
        });
    }

    private void requestOneShot() {
        if (!checkCamera()) {
            return;
        }
        camera.request().oneShotRequest().subscribe(new Action1<RxCameraData>() {
            @Override
            public void call(RxCameraData rxCameraData) {
                showLog("one shot request, cameraData.length: " + rxCameraData.cameraData.length);
            }
        });
    }

    private void requestPeriodicData() {
        if (!checkCamera()) {
            return;
        }
        camera.request().periodicDataRequest(1000).subscribe(new Action1<RxCameraData>() {
            @Override
            public void call(RxCameraData rxCameraData) {
                showLog("periodic request, cameraData.length: " + rxCameraData.cameraData.length);
            }
        });
    }

    private void requestTakePicture() {
        if (!checkCamera()) {
            return;
        }
        camera.request().takePictureRequest(true, new Func() {
            @Override
            public void call() {
                showLog("Captured!");
            }
        }, 480, 640, ImageFormat.JPEG, true).subscribe(new Action1<RxCameraData>() {
            @Override
            public void call(RxCameraData rxCameraData) {
                String path = Environment.getExternalStorageDirectory() + "/test.jpg";
                File file = new File(path);
                Bitmap bitmap = BitmapFactory.decodeByteArray(rxCameraData.cameraData, 0, rxCameraData.cameraData.length);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                        rxCameraData.rotateMatrix, false);
                try {
                    file.createNewFile();
                    FileOutputStream fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                showLog("Save file on " + path);
            }
        });
    }

    private void actionZoom() {
        if (!checkCamera()) {
            return;
        }
        camera.action().zoom(10).subscribe(new Subscriber<RxCamera>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("zoom error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("zoom success: " + rxCamera);
            }
        });
    }

    private void actionSmoothZoom() {
        if (!checkCamera()) {
            return;
        }
        camera.action().smoothZoom(10).subscribe(new Subscriber<RxCamera>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("zoom error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("zoom success: " + rxCamera);
            }
        });
    }

    private void actionOpenFlash() {
        if (!checkCamera()) {
            return;
        }
        camera.action().flashAction(true).subscribe(new Subscriber<RxCamera>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("open flash error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("open flash");
            }
        });
    }

    private void actionCloseFlash() {
        if (!checkCamera()) {
            return;
        }
        camera.action().flashAction(false).subscribe(new Subscriber<RxCamera>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("close flash error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("close flash");
            }
        });
    }

    private boolean checkCamera() {
        if (camera == null || !camera.isOpenCamera()) {
            return false;
        }
        return true;
    }
}
