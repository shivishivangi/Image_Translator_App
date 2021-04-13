package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage;
import com.google.firebase.ml.naturallanguage.languageid.FirebaseLanguageIdentification;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslateLanguage;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslator;
import com.google.firebase.ml.naturallanguage.translate.FirebaseTranslatorOptions;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class Translation extends AppCompatActivity {
    private HashMap<String, String> TextLangMap = new HashMap<>();
    private StringBuilder TranslatedText = new StringBuilder();
    private FirebaseTranslator fr_en_Translator;
    private boolean ModelDownloaded = false;

    private Button translateButton;
    private Button camButton;
    private Button photoButton;
    private ImageView image;
    private static final int SELECT_PICTURE = 100;
    private static final String TAG = "SelectImageActivity";
    private ContentValues values;
    private Bitmap imageBitmap;
    private String currentPhotoPath;
    private static final int REQUEST_TAKE_PHOTO = 1;
    private TextView translated;
    private static final int CAMERA_REQUEST = 1888;
    private ImageView imageView;
    private static final int MY_CAMERA_PERMISSION_CODE = 100;

    //Code borrowed from: https://www.youtube.com/watch?v=ondCeqlAwEI
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation);

        /*camButton = (Button) findViewById(R.id.camera);
        camButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });*/
        photoButton = (Button) findViewById(R.id.photogallery);
        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPhotoGallery();
            }
        });
        image = (ImageView) findViewById(R.id.imageView);
        translateButton = (Button) findViewById(R.id.translate);
        translateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openTranslateProcess();
            }
        });
        translated = findViewById(R.id.translatedtext);
    }
    public void openTranslateProcess() {
        imageBitmap = ((BitmapDrawable)image.getDrawable()).getBitmap();
        if (imageBitmap != null) {
            this.translated.setText("");
            runTextRecognition(imageBitmap);
            Iterator hmIterator = this.TextLangMap.entrySet().iterator();
            while (hmIterator.hasNext()) {
                HashMap.Entry mapElement = (HashMap.Entry) hmIterator.next();
                String keyText = ((String) mapElement.getKey());
                String keyValue = ((String) mapElement.getValue());
            }
            this.translated.append(this.TranslatedText.toString());
        }
    }

    public void openPhotoGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }

    static final int REQUEST_IMAGE_CAPTURE = 1;
    public void openCamera() {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            //if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                //startActivityForResult(takePictureIntent, CAMERA_REQUEST);
            //}
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // Create the File where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                } catch (IOException ex) {
                    // Error occurred while creating the File
                }
                // Continue only if the File was successfully created
                if (photoFile != null) {
                    Uri photoURI = FileProvider.getUriForFile(this,
                            "com.example.android.fileprovider",
                            photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST);
                }   else {
                    startActivityForResult(takePictureIntent, CAMERA_REQUEST);
                }
                //startActivityForResult(takePictureIntent, CAMERA_REQUEST);
            }
            //onActivityResult(SELECT_PICTURE, RESULT_OK, takePictureIntent);
        //takePictureIntent.setType("image/*");
        //takePictureIntent.setAction(Intent.ACTION_GET_CONTENT);
        //startActivityForResult(Intent.createChooser(takePictureIntent, "Select Picture"), SELECT_PICTURE);
        }


    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            Bitmap resized = Bitmap.createScaledBitmap(imageBitmap, 124, 124, true);
            image.setImageBitmap(resized);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (resultCode == RESULT_OK) {
                    if (requestCode == SELECT_PICTURE) {
                        // Get the url from data
                        final Uri selectedImageUri = data.getData();
                        if (null != selectedImageUri) {
                            // Get the path from the Uri
                            /**File file = new File(selectedImageUri.toString());
                             try {
                             FileInputStream fileInputStream = new FileInputStream(file);
                             imageBitmap = BitmapFactory.decodeStream(fileInputStream);
                             } catch(Exception e) {
                             }
                             **/
                            String path = getPathFromURI(selectedImageUri);
                            Log.i(TAG, "Image Path : " + path);
                            // Set the image in ImageView
                            findViewById(R.id.imageView).post(new Runnable() {
                                @Override
                                public void run() {
                                    ((ImageView) findViewById(R.id.imageView)).setImageURI(selectedImageUri);
                                }
                            });

                        }
                    }
                }
            }
        }).start();
    }
    private void handlePermission() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //ask for permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    SELECT_PICTURE);
        }
    }
    // Code borrowed from: http://www.coderzheaven.com/2012/04/20/select-an-image-from-gallery-in-android-and-show-it-in-an-imageview/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case SELECT_PICTURE:
                for (int i = 0; i < permissions.length; i++) {
                    String permission = permissions[i];
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
                        if (showRationale) {
                            //  Show your own message here
                        } else {
                            showSettingsAlert();
                        }
                    }
                }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    public String getPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }

    private void showSettingsAlert() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Alert");
        alertDialog.setMessage("App needs to access the Camera.");
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "DONT ALLOW",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        //finish();
                    }
                });
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "SETTINGS",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        openAppSettings(Translation.this);
                    }
                });
        alertDialog.show();
    }
    public static void openAppSettings(final Activity context) {
        if (context == null) {
            return;
        }
        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + context.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(i);
    }
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }
    // Code borrowed from: https://firebase.google.com/docs/ml-kit/android/translate-text
    // and https://firebase.google.com/docs/ml-kit/android/recognize-text
    private void prepareTranslationModel(){
        FirebaseTranslatorOptions options =
                new FirebaseTranslatorOptions.Builder()
                        .setSourceLanguage(FirebaseTranslateLanguage.FR)
                        .setTargetLanguage(FirebaseTranslateLanguage.EN)
                        .build();
        this.fr_en_Translator =
                FirebaseNaturalLanguage.getInstance().getTranslator(options);

        FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions.Builder()
                .requireWifi()
                .build();
        this.fr_en_Translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void v) {
                                // Model downloaded successfully. Okay to start translating.
                                // (Set a flag, unhide the translation UI, etc.)
                                setModelDownloaded();
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                // Model couldn’t be downloaded or other internal error.
                                // ...
                                String msg = "Translator model can't be downloaded";
                            }
                        });
    }
    private void setModelDownloaded(){
        this.ModelDownloaded = true;
    }

    private void runTextRecognition(Bitmap bmp) {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bmp);
        /*
        FirebaseVisionTextRecognizer recognizer = FirebaseVision.getInstance()
                .getOnDeviceTextRecognizer();
        */
        FirebaseVisionTextRecognizer recognizer = FirebaseVision.getInstance()
                .getCloudTextRecognizer();

        recognizer.processImage(image)
                .addOnSuccessListener(
                        new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(FirebaseVisionText texts) {
                                processTextRecognitionResult(texts);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                String  msg = e.getMessage();
                                // Task failed with an exception
                                e.printStackTrace();
                            }
                        });
    }
    private void runLanguageDetection(final String text){
        FirebaseLanguageIdentification languageIdentifier =
                FirebaseNaturalLanguage.getInstance().getLanguageIdentification();
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener(
                        new OnSuccessListener<String>() {
                            @Override
                            public void onSuccess(String languageCode) {
                                if (languageCode != "und") {
                                    processLanguageCode(text, languageCode);
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                // Model couldn’t be loaded or other internal error.
                                // ...
                                String msg = e.getMessage();
                            }
                        });
    }
    private void processTextRecognitionResult(FirebaseVisionText texts) {
        List<FirebaseVisionText.TextBlock> blocks = texts.getTextBlocks();
        this.TextLangMap.clear();

        if (blocks.size() == 0) {
            String msg = "No text found";
            return;
        }

        for (int i = 0; i < blocks.size(); i++) {
            List<FirebaseVisionText.Line> lines = blocks.get(i).getLines();
            for (int j = 0; j < lines.size(); j++) {
                List<FirebaseVisionText.Element> elements = lines.get(j).getElements();
                for (int k = 0; k < elements.size(); k++) {
                    this.TextLangMap.put(elements.get(k).getText(), "en");
                    runLanguageDetection(elements.get(k).getText());
                }
            }
        }
    }
    private void processLanguageCode(String key, String langCode){
        //Add the language code corresponding to the text we just retrieved
        this.TextLangMap.put(key, langCode);
        this.TranslatedText.append(key + ":" + langCode);
        //procesLanguageTranslation(key, langCode);
    }
    private void procesLanguageTranslation(final String text, final String langCode) {
        this.prepareTranslationModel();
        this.fr_en_Translator.translate(text)
                .addOnSuccessListener(
                        new OnSuccessListener<String>() {
                            @Override
                            public void onSuccess(String translatedText) {
                                // Translation successful.
                                processTranslatedText(text, langCode);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                // Error.
                                // ...
                                String msg = "Language translation failed";
                            }
                        });
    }
    private void processTranslatedText(String originalText, String translatedText){
        this.TranslatedText.append(translatedText);
    }
}