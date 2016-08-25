package com.byteshaft.hairrestorationcenter.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.byteshaft.hairrestorationcenter.MainActivity;
import com.byteshaft.hairrestorationcenter.R;
import com.byteshaft.hairrestorationcenter.utils.AppGlobals;
import com.byteshaft.hairrestorationcenter.utils.Helpers;
import com.byteshaft.hairrestorationcenter.utils.WebServiceHelpers;
import com.byteshaft.requests.HttpRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HealthInformation extends Fragment implements
        HttpRequest.OnReadyStateChangeListener {

    private Spinner mGenderSpinner;
    private EditText mAgeEntry;
    private ProgressDialog mProgressDialog;
    private ListView mListView;
    private ArrayList<JSONObject> fieldData;
    private ArrayList<Integer> idsArray;
    private HashMap<Integer, String> answersList;
    private StringBuilder stringBuilder = new StringBuilder();
    private ArrayList<String> requiredFields;
    private int idForGender = 2;

    // Data sets
    private ArrayList<JSONObject> mDefaultItems = new ArrayList<>();
    private ArrayList<JSONObject> mCheckableItems = new ArrayList<>();
    private ArrayList<JSONObject> mFormFields = new ArrayList<>();

    private List<String> checkBoxAnswer;
    private LinearLayout mLinearLayout;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View mBaseView = inflater.inflate(R.layout.health_information, container, false);
        fieldData = new ArrayList<>();
        idsArray = new ArrayList<>();
        answersList = new HashMap<>();
        requiredFields = new ArrayList<>();
        checkBoxAnswer = new ArrayList<>();
        mAgeEntry = (EditText) mBaseView.findViewById(R.id.age);
        mGenderSpinner = (Spinner) mBaseView.findViewById(R.id.gender);
        mLinearLayout = (LinearLayout) mBaseView.findViewById(R.id.main_layout);
        mListView = (ListView) mBaseView.findViewById(R.id.fields_list_view);
        mProgressDialog = Helpers.getProgressDialog(getActivity());
        getFieldsDetails();
        return mBaseView;
    }

    private void getFieldsDetails() {
        mProgressDialog.show();
        HttpRequest request = new HttpRequest(getActivity().getApplicationContext());
        request.setOnReadyStateChangeListener(this);
        request.open("GET", AppGlobals.QUESTION_LIST);
        request.send();
    }

    private Runnable executeTask(final boolean value) {
        return new Runnable() {
            @Override
            public void run() {
                new CheckInternet(value).execute();
            }
        };
    }

    @Override
    public void onReadyStateChange(HttpRequest request, int readyState) {
        switch (readyState) {
            case HttpRequest.STATE_DONE:
                mProgressDialog.dismiss();
                switch (request.getStatus()) {
                    case HttpURLConnection.HTTP_OK:
                        if (request.getConnection().getRequestMethod().equals("POST")) {
                            try {
                                processSuccess(request);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } else {
                            try {
                                parseJsonAndSetUi(request.getResponseText());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                }
        }
    }

    private void processSuccess(HttpRequest request) throws JSONException {
        Log.i("TAG", request.getResponseText());
        JSONObject jsonObject = new JSONObject(request.getResponseText());
        if (jsonObject.getString("Message").equals("Successfully")) {

            AlertDialog.Builder alertDialogBuilder =
                    new AlertDialog.Builder(getActivity());
            alertDialogBuilder.setTitle("Success");
            alertDialogBuilder.setMessage("Your details have uploaded " +
                    "successfully.")
                    .setCancelable(false).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    AppGlobals.sConsultationSuccess = true;
                    ConsultationFragment.sUploaded = false;
                    dialog.dismiss();
                    MainActivity.loadFragment(new EducationFragment());
                }
            });
            AlertDialog alertDialog = alertDialogBuilder.create();
            alertDialog.show();
        }
    }

    private void parseJsonAndSetUi(String data) throws JSONException {
        JSONObject rootObject = new JSONObject(data);
        if (rootObject.getString("Message").equals("Successfully")) {
            JSONArray detailsArray = rootObject.getJSONArray("details");
            for (int i = 0; i < detailsArray.length(); i++) {
                JSONObject json = detailsArray.getJSONObject(i);
                String itemTitle = json.getString("title");
                if (itemTitle.equals("Age") || itemTitle.equals("Gender")) {
                    continue;
                }
                String itemType = json.getString("field_type");
                if (itemType.equals("checkbox")) {
                    mCheckableItems.add(json);
                } else if (itemType.equals("textbox")) {
                    mFormFields.add(json);
                }
            }
        } else {
            AppGlobals.alertDialog(getActivity(), "Not Found", "Nothing found");
        }
        ArrayList<JSONObject> realData = new ArrayList<>();
        realData.addAll(mDefaultItems);
        realData.addAll(mCheckableItems);
        realData.addAll(mFormFields);
        Adapter adapter = new Adapter(getActivity().getApplicationContext(),
                R.layout.delegate_consultation_fields, realData);
        mListView.setAdapter(adapter);
        mListView.addFooterView(getSubmitButton());
    }

    private Button getSubmitButton() {
        Button buttonSubmit = new Button(getActivity());
        buttonSubmit.setBackgroundColor(Color.parseColor("#05262F"));
        buttonSubmit.setTextColor(Color.parseColor("#ffffffff"));
        buttonSubmit.setText("SUBMIT");
        buttonSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (AppGlobals.sEntryId == 0) {
                    Toast.makeText(getActivity(), "Please try again process failed",
                            Toast.LENGTH_SHORT).show();
                    MainActivity.loadFragment(new ConsultationFragment());
                } else {
                    boolean result = validateEditText();
                    Log.i("boolean", " " + result);
                    if (result) {
                        mProgressDialog.show();
                        if (AppGlobals.sIsInternetAvailable) {
                            new SendData(false).execute();
                        } else {
                            Helpers.alertDialog(getActivity(), "No internet", "Please check your " +
                                            "internet connection",
                                    executeSendData(true));
                        }
                    }
                }
            }
        });
        return buttonSubmit;
    }

    class Adapter extends ArrayAdapter<JSONObject> {

        public Adapter(Context context, int resource, ArrayList<JSONObject> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ViewHolder holder;
            if (convertView == null) {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                convertView = inflater.inflate(R.layout.delegate_consultation_fields, parent, false);
                holder = new ViewHolder();
                holder.title = (TextView) convertView.findViewById(R.id.field_title);
                holder.editText = (EditText) convertView.findViewById(R.id.field_answer);
                holder.editTextLayout = (LinearLayout) convertView.findViewById(R.id.edit_text_layout);
                holder.checkBoxLayout = (LinearLayout) convertView.findViewById(R.id.checkbox_layout);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            JSONObject item = getItem(position);
            String itemType = item.optString("field_type");
            int mandatory = item.optInt("required");
            String title = item.optString("title");
            holder.title.setText(
                    getFormattedTitle(title, mandatory), TextView.BufferType.SPANNABLE);
            if (itemType.equals("checkbox")) {
                holder.checkBoxLayout.removeAllViews();
                holder.editTextLayout.setVisibility(View.GONE);
                holder.checkBoxLayout.setVisibility(View.VISIBLE);
                JSONArray checkBoxes = item.optJSONArray("field_data");
                for (int i = 0; i < checkBoxes.length(); i++) {
                    CheckBox checkBox = new CheckBox(getActivity());
                    checkBox.setText((String) checkBoxes.opt(i));
                    checkBox.setTextColor(getResources().getColor(android.R.color.white));
                    checkBox.setButtonDrawable(getResources().getDrawable(
                            R.drawable.checkbox_background));
                    holder.checkBoxLayout.addView(checkBox);
                }
            } else if (itemType.equals("textbox")){
                holder.editTextLayout.setVisibility(View.VISIBLE);
                holder.checkBoxLayout.setVisibility(View.GONE);
            }
            return convertView;
        }
    }

    private SpannableStringBuilder getFormattedTitle(String text, int mandatory) {
        SpannableStringBuilder realText = new SpannableStringBuilder();
        if (mandatory == 1) {
            String asterisk = "* ";
            SpannableString mandatorySpannable = new SpannableString(asterisk);
            mandatorySpannable.setSpan(
                    new ForegroundColorSpan(Color.RED), 0, asterisk.length(), 0);
            realText.append(mandatorySpannable);
        }
        SpannableString whiteSpannable = new SpannableString(text);
        whiteSpannable.setSpan(new ForegroundColorSpan(Color.WHITE), 0, text.length(), 0);
        realText.append(whiteSpannable);
        return realText;
    }

    private boolean validateEditText() {
        stringBuilder = new StringBuilder();
        boolean value = false;
        for (int id : idsArray) {
            if (answersList.size() >= (requiredFields.size() - 1)) {
                if (answersList.containsKey(id)) {
                    value = true;
                    stringBuilder.append(String.format("data[%d]=%s&", id, answersList.get(id)));
                }
            } else if (answersList.size() < requiredFields.size()) {
                value = false;
                Toast.makeText(getActivity(), "All required fields must be filled", Toast.LENGTH_SHORT).show();
                break;
            } else {
                value = false;
                Toast.makeText(getActivity(), "All required fields must be filled", Toast.LENGTH_SHORT).show();
                break;
            }
        }
        stringBuilder.append(String.format("user_id=%s&", AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_USER_ID)));
        stringBuilder.append(String.format("entry_id=%s&", AppGlobals.sEntryId));
        stringBuilder.append(String.format("data[%d]=%s", idForGender, mGenderSpinner.getSelectedItem().toString()));
        Log.i("String", stringBuilder.toString());
        return value;
    }

    class ViewHolder {
        public TextView title;
        public EditText editText;
        public LinearLayout editTextLayout;
        public LinearLayout checkBoxLayout;
        public Button submitButton;
    }

    private void sendConsultationData(String data) {
        HttpRequest request = new HttpRequest(getActivity().getApplicationContext());
        request.setOnReadyStateChangeListener(this);
        request.open("POST", AppGlobals.CONSULTATION_STEP_2);
        request.send(data);
    }

    class CheckInternet extends AsyncTask<String, String, Boolean> {

        public CheckInternet(boolean checkInternet) {
            this.checkInternet = checkInternet;
        }

        private boolean checkInternet = false;
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("Loading ...");
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            boolean isInternetAvailable = false;
            if (AppGlobals.sIsInternetAvailable) {
                isInternetAvailable = true;
            } else if (checkInternet) {
                if (WebServiceHelpers.isNetworkAvailable()) {
                    isInternetAvailable = true;
                }

            }
            return isInternetAvailable;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            progressDialog.dismiss();
            if (aBoolean) {
                getFieldsDetails();
            } else {
                Helpers.alertDialog(getActivity(), "No internet", "Please check your internet connection",
                        executeTask(true));
            }
        }
    }


    class SendData extends AsyncTask<String, String, Boolean> {

        public SendData(boolean checkInternet) {
            this.checkInternet = checkInternet;
        }

        private boolean checkInternet = false;
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("Sending...");
            progressDialog.setIndeterminate(false);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            boolean isInternetAvailable = false;
            if (AppGlobals.sIsInternetAvailable) {
                isInternetAvailable = true;
            } else if (checkInternet) {
                if (WebServiceHelpers.isNetworkAvailable()) {
                    isInternetAvailable = true;
                }
            }
            return isInternetAvailable;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            progressDialog.dismiss();
            if (aBoolean) {
                sendConsultationData(stringBuilder.toString());
            } else {
                Helpers.alertDialog(getActivity(), "No internet", "Please check your internet connection",
                        executeSendData(true));
            }
        }
    }

    private Runnable executeSendData(final boolean value) {
        return new Runnable() {
            @Override
            public void run() {
                new SendData(value).execute();
            }
        };
    }
}
