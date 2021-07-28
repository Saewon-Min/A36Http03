package com.kosmo.a36http03;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AsyncPlayer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "iKosmo";

    ImageView ivPicture;
    TextView tvHtml1;
    String filePath1; // 파일의 절대 경로


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        ivPicture = findViewById(R.id.ivPicture);
        tvHtml1 = findViewById(R.id.tvHtml1);

        /*
            Manifest에 설정된 권한에 대해 사용자에게 물어본다.
            만약 사용자가 허용하지 않으면 해당 기능은 사용할 수 없다.
        */
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
            !=PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);

        }

    } //// onCreate()

    // 이미지 선택
    public void onBtnGetPicture(View v){
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(MediaStore.Images.Media.CONTENT_TYPE);
        intent.setData(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent,1);
    }

    // 파일 업로드
    public void onBtnUpload(View v){

        // 텍스트뷰 내용 비우기
        tvHtml1.setText("");

        // 파라미터를 맵에 저장
        HashMap<String, String> param1 = new HashMap<>();
        param1.put("userid","김선호");
        param1.put("userpwd","패스워드");

        HashMap<String, String> param2 = new HashMap<>();
        param2.put("filename",filePath1);

        // AsyncTask를 통해 생성한 객체를 통해 HTTP 통신 시작
        UploadAsync newworkTask = new
                UploadAsync(getApplicationContext(), param1, param2);

        // doInBackground() 호출
        newworkTask.execute();

    }

    // 앱 종료하기
    public void onBtnFinish(View v){
        finish();
    }

    // 갤러리 리스트에서 사진 데이터를 가져오기 위한 메소드
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if(requestCode==1){
            if(resultCode==RESULT_OK){
                Uri selPhotoUri = data.getData();
                showCapturedImage(selPhotoUri);
            }
        }


    }

    // 사진의 절대 경로 구하기(사용자 정의 메소드)
    private String getRealPathFromURI(Uri contentUri){

        int column_index=0;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri,proj,null,null,null);
        if(cursor.moveToFirst()){
            column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        }

        return cursor.getString(column_index);
    }

    /*
    사진의 회전값을 처리해주는 메소드
        : 사진의 회전값을 처리하지 않으면 사진을
          찍은 방향대로 이미지뷰에 설정할 수 없게 된다.
     */
    private int exifOrientationToDegrees(int exifOrientation){
        if(exifOrientation == ExifInterface.ORIENTATION_ROTATE_90){
            return 90;
        }
        else if(exifOrientation == ExifInterface.ORIENTATION_ROTATE_180){
            return 180;
        }
        else if(exifOrientation == ExifInterface.ORIENTATION_ROTATE_270){
            return 270;
        }
        return 0;
    }


    // 사진을 정방향대로 회전하기위한 메소드(사용자 정의 함수)
    private Bitmap rotate(Bitmap src, float degree){
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(src,0,0,src.getWidth(), src.getHeight(), matrix,true);

    }


    // 사진의 절대 경로를 구한 후 이미지뷰에 선택한 사진을 설정함.
    private void showCapturedImage(Uri imageUri){
        filePath1 = getRealPathFromURI(imageUri); // 사용자 정의 함수
        Log.d(TAG, "path1:"+filePath1);
        ExifInterface exifInterface = null;
        try{
            exifInterface = new ExifInterface(filePath1);
        }catch (IOException e){
            e.printStackTrace();
        }

        int exifOrientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        int exifDegree = exifOrientationToDegrees(exifOrientation); // 사용자 정의 함수

        Bitmap bitmap = BitmapFactory.decodeFile(filePath1);
        Bitmap rotatedBitmap = rotate(bitmap, exifDegree); // 사용자 정의 함수
        Bitmap scaleBitmap = Bitmap.createScaledBitmap(rotatedBitmap,800,800,false);
        bitmap.recycle();

        ivPicture.setImageBitmap(scaleBitmap);

    }

    // 서버와 통신을 위한 클래스
    public class UploadAsync extends AsyncTask<Object,Integer, JSONObject>{

        private Context mContext;
        private HashMap<String, String> param; // 파라미터
        private HashMap<String, String> files; // 사진 파일

        public UploadAsync(Context context, HashMap<String, String> param, HashMap<String, String> files){
            mContext = context;
            this.param = param;
            this.files = files;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected JSONObject doInBackground(Object... objects) {
            JSONObject rtn = null;

            try{
                // Rest API 서버의 요청명을 기술
                String sUrl = getString(R.string.server_addr)+
                        "/jsonrestapi/fileUpload/uploadAndroid.do";
                /*
                단말기의 사진을 서버로 업로드하기 위한 객체 생성 및 메소드 호출
                FileUpload 클래스는 기존 내용을 그대로 수정없이 가져다 쓰면된다.
                 */
                FileUpload multipartUpload = new FileUpload(sUrl, "UTF-8");
                rtn = multipartUpload.upload(param,files);
                Log.d(TAG, rtn.toString());

            }catch (IOException e){
                e.printStackTrace();
            }
            return rtn;


        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(JSONObject jsonObject) {
            super.onPostExecute(jsonObject);

            if(jsonObject != null){
                // 결과 데이터를 텍스트뷰에 출력한다.
                tvHtml1.setText(jsonObject.toString());
                try{
                    if(jsonObject.getInt("success") == 1){
                        Toast.makeText(mContext,"파일 업로드 성공",
                                Toast.LENGTH_SHORT).show();
                    }
                }catch (JSONException e){
                    e.printStackTrace();
                }

            }else {
                Toast.makeText(mContext,"파일 업로드 실패",
                        Toast.LENGTH_SHORT).show();
            }

        }
    }


}