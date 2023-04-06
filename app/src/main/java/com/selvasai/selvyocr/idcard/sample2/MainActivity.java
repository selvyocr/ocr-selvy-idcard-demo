package com.selvasai.selvyocr.idcard.sample2;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.selvasai.selvyocr.idcard.ImageRecognizer;
import com.selvasai.selvyocr.idcard.sample2.util.Utils;
import com.selvasai.selvyocr.idcard.util.BuildInfo;
import com.selvasai.selvyocr.idcard.util.LicenseChecker;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    /**
     * 암/복호화 키값: 32 Byte (256 Bit)
     * 적정 값으로 정의해서 사용
     */
    protected static final byte[] AES256_KEY = {
            's', 'e', 'l', 'v', 'a', 's', 'a', 'i',
            'i', 'a', 's', 'a', 'v', 'l', 'e', 's',
            'i', 'a', 's', 'a', 'v', 'l', 'e', 's',
            's', 'e', 'l', 'v', 'a', 's', 'a', 'i'};

    /**
     * 초기 벡터: 16 Byte(128 Bit)
     * 적정 값으로 정의해서 사용
     */
    protected static final byte[] AES256_IV = {
            's', 'e', 'l', 'v', 'a', 's', 'a', 'i',
            'i', 'a', 's', 'a', 'v', 'l', 'e', 's'};

    private static final int REQ_PERMISSION_RESULT = 0;

    private static final int MSG_RESULT_SUCCESS = 0x0001;
    private static final int MSG_RESULT_FAIL = 0x0002;

    private ImageView mResultImageView;
    private TextView mResultTextView;
    private TextView mIsValidAreaTextView;

    private FloatingActionButton mFab;

    private static final boolean GET_MASKED_IMAGE = true; // 마스킹된 이미지를 받기 위한 플래그, true - 마스킹된 이미지를 받음
    private static final boolean LOAD_LIBRARY_BY_PATH = false;   // 지정된 경로의 so 파일을 읽어오기 위한 플래그, true - 지정된 경로의 so 파일 읽어서 사용하는 기능 사용

    private ActivityResultLauncher<Intent> activityResultLauncher;

    /**
     * OCR 결과 리스너 <br/>
     * 인식에 성공하면 인식결과 정보를 넘겨줌
     */
    private ImageRecognizer.RecognitionListener mRecognitionListener = new ImageRecognizer.RecognitionListener() {

        /**
         * 인식에 성공하여 결과값 전달
         * @param resultText 인식 결과 데이터
         * @param rrnRect 주민등록번호 마스킹 영역
         * @param licenseNumberRect 운전면허번호 마스킹 영역
         * @param photoRect 증명사진 영역
         * @param persImage 결과 이미지
         * @param isValidCard 흑백 복사본 판별, 흑백복사본인 경우 false
         * @param isValidRegistrationNumber 주민등록번호/외국인등록번호 유효성 여부
         * @param isValidArea 신분증 영역 검출 여부
         */
        @Override
        public void onFinish(ArrayList<String> resultText, Rect rrnRect, Rect licenseNumberRect, Rect photoRect, Bitmap persImage,
                             boolean isValidCard, boolean isValidRegistrationNumber, boolean isValidArea) {
            Log.d("onFinish", String.join(" ", resultText) );
            Utils.progressDialog(MainActivity.this, false, null);
            Toast.makeText(MainActivity.this, "onFinish.", Toast.LENGTH_SHORT).show();

            if (resultText != null) {
                viewSetting(resultText);
            }
            Canvas c = new Canvas(persImage);
            if (!GET_MASKED_IMAGE && rrnRect != null && licenseNumberRect != null) {
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.BLACK);
                c.drawRect(rrnRect, paint);
                c.drawRect(licenseNumberRect, paint);
            }
            if (photoRect != null) {
                Paint photoPaint = new Paint();
                photoPaint.setStyle(Paint.Style.STROKE);
                photoPaint.setStrokeWidth(5.f);
                photoPaint.setColor(Color.RED);
                c.drawRect(photoRect, photoPaint);
            }

            mResultImageView.setImageBitmap(persImage);
            mResultImageView.setVisibility(View.VISIBLE);

            if (isValidCard) {
                ((TextView)findViewById(R.id.tv_is_valid_color_idcard)).setText(R.string.valid_color_idcard);
            } else {
                ((TextView)findViewById(R.id.tv_is_valid_color_idcard)).setText(R.string.not_valid_color_idcard);
            }

            if (isValidRegistrationNumber) {
                ((TextView)findViewById(R.id.tv_is_valid_rrn)).setText(R.string.valid_rrn);
            } else {
                ((TextView)findViewById(R.id.tv_is_valid_rrn)).setText(R.string.not_valid_rrn);
            }

            if (!isValidArea) {
                mIsValidAreaTextView.setVisibility(View.VISIBLE);
            } else {
                mIsValidAreaTextView.setVisibility(View.GONE);
            }
        }

        /**
         * 인식에 실패
         * @param code 오류코드
         *              {@link ImageRecognizer#ERROR_CODE_FAIL}: 인식 실패
         *              {@link ImageRecognizer#ERROR_CODE_FILE_NOT_FOUND}: ROM 파일을 찾지 못함
         *              {@link ImageRecognizer#ERROR_CODE_LICENSE_CHECK_FAILED}: 라이선스 만료
         *              {@link ImageRecognizer#ERROR_CODE_REGISTRATION_NUMBER_FAIL}: 주민등록번호 인식 실패
         *              {@link ImageRecognizer#ERROR_CODE_LICENSE_NUMBER_FAIL}: 운전면허번호 인식 실패
         */
        @Override
        public void onError(int code) {
            Utils.progressDialog(MainActivity.this, false, null);
            if (ImageRecognizer.ERROR_CODE_FAIL == code) {
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_FAIL", Toast.LENGTH_SHORT).show();
            } else if (ImageRecognizer.ERROR_CODE_FILE_NOT_FOUND == code) {
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_FILE_NOT_FOUND", Toast.LENGTH_SHORT).show();
            } else if (ImageRecognizer.ERROR_CODE_LICENSE_CHECK_FAILED == code) {
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_LICENSE_CHECK_FAILED", Toast.LENGTH_SHORT).show();
            } else if (ImageRecognizer.ERROR_CODE_REGISTRATION_NUMBER_FAIL == code) {
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_REGISTRATION_NUMBER_FAIL", Toast.LENGTH_SHORT).show();
            } else if (ImageRecognizer.ERROR_CODE_LICENSE_NUMBER_FAIL == code){
                Toast.makeText(MainActivity.this, "error code = " + "ERROR_CODE_LICENSE_NUMBER_FAIL", Toast.LENGTH_SHORT).show();
            }


        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_main);
        toolbar.setTitle(BuildInfo.getVersion());
        setSupportActionBar(toolbar);

        initView();

        if (LOAD_LIBRARY_BY_PATH == false) {
            Date date = LicenseChecker.getExpiredDate(getApplicationContext());
            String dateToString = new SimpleDateFormat("yyyy-MM-dd").format(date);
            ((TextView)findViewById(R.id.tv_license_expiry_date)).setText("라이선스 만료 : ~" + dateToString );
        }

        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Utils.progressDialog(MainActivity.this, true, getString(R.string.recognize));
                    Intent data = result.getData();
                    if (null == data) {
                        Utils.progressDialog(MainActivity.this, false, null);
                        Toast.makeText(getApplicationContext(), "getData is null", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Uri uri = data.getData();
                    Bitmap inputImage = null;
                    try {
                        inputImage = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
                        // Use the `bitmap` object as needed
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (!LicenseChecker.isValidLicense(getApplicationContext())) {
                        Utils.progressDialog(MainActivity.this, false, null);
                        Toast.makeText(getApplicationContext(), "License expired!", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (null == inputImage) {
                        Utils.progressDialog(MainActivity.this, false, null);
                        Toast.makeText(getApplicationContext(), "Image Load Fail!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ImageRecognizer imageRecognizer = new ImageRecognizer(getApplicationContext(), AES256_KEY, AES256_IV);
                    imageRecognizer.maskInputImage(true);


                    imageRecognizer.startRecognition(inputImage, mRecognitionListener);
                }
        );
    }


    @Override
    protected void onDestroy() {
        mResultImageView = null;
        super.onDestroy();
    }

    /**
     * 초기화
     */
    private void initView() {
        LinearLayout textImageLayout = (LinearLayout) findViewById(R.id.text_image_layout);

        mResultImageView = new ImageView(MainActivity.this);
        mResultImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mResultImageView.setVisibility(View.GONE);
        textImageLayout.addView(mResultImageView);

        mResultTextView = new TextView(MainActivity.this);
        mResultTextView.setText("Image load fail");
        mResultTextView.setPadding(20, 15, 15, 15);
        mResultTextView.setGravity(Gravity.CENTER);
        mResultTextView.setTextSize(20);
        mResultTextView.setVisibility(View.GONE);
        textImageLayout.addView(mResultTextView);

        mIsValidAreaTextView = findViewById(R.id.tv_valid_area);

        // 카메라 화면으로 이동 버튼
        mFab = (FloatingActionButton) findViewById(R.id.fab);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestPermission();
            }
        });
    }

    private void requestPermission() {
        ArrayList<String> permissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (permissions.size() > 0) {
            String[] temp = new String[permissions.size()];
            permissions.toArray(temp);
            ActivityCompat.requestPermissions(MainActivity.this, temp, REQ_PERMISSION_RESULT);
        } else {
            openGallery();
        }
    }

    private void openGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        activityResultLauncher.launch(intent);
    }

    /**
     * 신분증 인식 결과를 화면에 보여줌
     *
     * @param resultText 신분증 인식 결과
     */
    private void viewSetting(ArrayList<String> resultText) {
        for (int i = 0; i < resultText.size(); i++) {
            resultText.set(i, ImageRecognizer.decrypt(resultText.get(i), AES256_KEY, AES256_IV));
        }
        String typeString = resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.TYPE.ordinal());

        // 신분증 종류
        TextView tvType = (TextView) findViewById(R.id.tv_type);
        tvType.setText(typeString);

        // 면허번호(운전면허증)
        LinearLayout licenseNumberLayout = (LinearLayout) findViewById(R.id.layout_license_number);
        View licenseNumberLayoutBar = findViewById(R.id.layout_license_number_bar);
        if (typeString.equalsIgnoreCase(ImageRecognizer.TYPE_DRIVER_LICENSE)) {
            TextView tvLicenseNumber = (TextView) findViewById(R.id.tv_license_number);
            String licenseNumber = resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.LICENSE_NUMBER.ordinal());
            tvLicenseNumber.setText(licenseNumber);

            licenseNumberLayout.setVisibility(View.VISIBLE);
            licenseNumberLayoutBar.setVisibility(View.VISIBLE);
        } else {
            licenseNumberLayout.setVisibility(View.GONE);
            licenseNumberLayoutBar.setVisibility(View.GONE);
        }

        // 이름
        TextView tvName = (TextView) findViewById(R.id.tv_name);
        tvName.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.NAME.ordinal()));

        // 주민등록번호
        LinearLayout rrnLayout = (LinearLayout) findViewById(R.id.layout_rrn);
        // 외국인등록번호(외국인등록증)
        LinearLayout arnLayout = (LinearLayout) findViewById(R.id.layout_arn);
        View rrnLayoutBar = findViewById(R.id.layout_rrn_bar);
        rrnLayoutBar.setVisibility(View.GONE);

        if (typeString.equalsIgnoreCase(ImageRecognizer.TYPE_ALIEN_CARD)) {
            arnLayout.setVisibility(View.VISIBLE);
            rrnLayout.setVisibility(View.GONE);

            TextView tvARN = (TextView) findViewById(R.id.tv_arn);
            String arn = resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.REGISTRATION_NUMBER.ordinal());
            tvARN.setText(arn);
        } else {
            arnLayout.setVisibility(View.GONE);
            rrnLayout.setVisibility(View.VISIBLE);

            TextView tvRRN = (TextView) findViewById(R.id.tv_rrn);
            String rrn = resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.REGISTRATION_NUMBER.ordinal());
            tvRRN.setText(rrn);
        }

        // 발급일
        TextView tvIssueDate = (TextView) findViewById(R.id.tv_issue_date);
        String date = resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.ISSUE_DATE.ordinal());
        tvIssueDate.setText(date);

        // 발급처
        LinearLayout organizationLayout = (LinearLayout) findViewById(R.id.layout_organization);
        // 국가지역(외국인등록증)
        LinearLayout nationLayout = (LinearLayout) findViewById(R.id.layout_nation);
        View nationLayoutBar = findViewById(R.id.layout_nation_bar);
        // 체류자격(외국인등록증)
        LinearLayout residentStatusLayout = (LinearLayout) findViewById(R.id.layout_resident_status);
        View residentStatusLayoutBar = findViewById(R.id.layout_resident_status_bar);
        if (typeString.equalsIgnoreCase(ImageRecognizer.TYPE_ALIEN_CARD)) {
            organizationLayout.setVisibility(View.GONE);
            nationLayout.setVisibility(View.VISIBLE);
            nationLayoutBar.setVisibility(View.VISIBLE);
            residentStatusLayout.setVisibility(View.VISIBLE);
            residentStatusLayoutBar.setVisibility(View.VISIBLE);

            TextView tvNation = (TextView) findViewById(R.id.tv_nation);
            tvNation.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.NATION.ordinal()));

            TextView tvResidentStatus = (TextView) findViewById(R.id.tv_resident_status);
            tvResidentStatus.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.RESIDENT_STATUS.ordinal()));
        } else {
            organizationLayout.setVisibility(View.VISIBLE);
            nationLayout.setVisibility(View.GONE);
            nationLayoutBar.setVisibility(View.GONE);
            residentStatusLayout.setVisibility(View.GONE);
            residentStatusLayoutBar.setVisibility(View.GONE);

            TextView tvOrganization = (TextView) findViewById(R.id.tv_organization);
            tvOrganization.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.ORGANIZATION.ordinal()));
        }

        // 만료일(운전면허증)
        LinearLayout licenseAptitudeDateLayout = (LinearLayout) findViewById(R.id.layout_license_aptitude_date);
        View licenseAptitudeDateLayoutBar = findViewById(R.id.layout_license_aptitude_date_bar);
        if (typeString.equalsIgnoreCase(ImageRecognizer.TYPE_DRIVER_LICENSE)) {
            licenseAptitudeDateLayout.setVisibility(View.VISIBLE);
            licenseAptitudeDateLayoutBar.setVisibility(View.VISIBLE);

            TextView tvLicenseAptitudeDate = (TextView) findViewById(R.id.tv_license_aptitude_date);
            tvLicenseAptitudeDate.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.LICENSE_APTITUDE_DATE.ordinal()));
        } else {
            licenseAptitudeDateLayout.setVisibility(View.GONE);
            licenseAptitudeDateLayoutBar.setVisibility(View.GONE);
        }

        // 종별구분(운전면허증)
        LinearLayout licenseTypeLayout = (LinearLayout) findViewById(R.id.layout_license_type);
        View licenseTypeLayoutBar = findViewById(R.id.layout_license_type_bar);
        if (typeString.equalsIgnoreCase(ImageRecognizer.TYPE_DRIVER_LICENSE)) {
            licenseTypeLayout.setVisibility(View.VISIBLE);
            licenseTypeLayoutBar.setVisibility(View.VISIBLE);

            TextView tvLicenseType = (TextView) findViewById(R.id.tv_license_type);
            tvLicenseType.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.LICENSE_TYPE.ordinal()));
        } else {
            licenseTypeLayout.setVisibility(View.GONE);
            licenseTypeLayoutBar.setVisibility(View.GONE);
        }

        // 주소(주민등록증/운전면허증)
        LinearLayout addressLayout = (LinearLayout) findViewById(R.id.layout_address);
        View addressLayoutBar = findViewById(R.id.layout_address_bar);
        if (!typeString.equalsIgnoreCase(ImageRecognizer.TYPE_ALIEN_CARD)) {
            addressLayout.setVisibility(View.VISIBLE);
            addressLayoutBar.setVisibility(View.VISIBLE);

            TextView tvAddress = (TextView) findViewById(R.id.tv_address);
            tvAddress.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.ADDRESS.ordinal()));
        } else {
            addressLayout.setVisibility(View.GONE);
            addressLayoutBar.setVisibility(View.GONE);
        }

        // 식별번호(운전면허증)
        LinearLayout licenseIdentificationNumberLayout = (LinearLayout) findViewById(R.id.layout_license_identification_number);
        View licenseIdentificationNumberLayoutBar = findViewById(R.id.layout_license_identification_number_bar);
        if (typeString.equalsIgnoreCase(ImageRecognizer.TYPE_DRIVER_LICENSE)) {
            licenseIdentificationNumberLayout.setVisibility(View.VISIBLE);
            licenseIdentificationNumberLayoutBar.setVisibility(View.VISIBLE);

            TextView tvLicenseIdentificationNumber = (TextView) findViewById(R.id.tv_license_identification_number);
            tvLicenseIdentificationNumber.setText(resultText.get(ImageRecognizer.RECOG_FIELD_IDCARD.IDENTIFICATION_NUMBER.ordinal()));
        } else {
            licenseIdentificationNumberLayout.setVisibility(View.GONE);
            licenseIdentificationNumberLayoutBar.setVisibility(View.GONE);
        }
    }

}