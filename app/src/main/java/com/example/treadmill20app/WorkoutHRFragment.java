package com.example.treadmill20app;
/*
Activity to create a new workout
*/

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.treadmill20app.adapters.WorkoutAdapter;
import com.example.treadmill20app.models.WorkoutObject;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class WorkoutHRFragment extends Fragment
        implements AdapterView.OnItemSelectedListener {

    public WorkoutHRFragment() {
        // Required empty constructor
    }

    private float maxHREntry = 0;
    private float maxVEntry = 0;
    private float durEntry = 0;
    private float zoneEntry = 0;
    private float inclEntry = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_workout_hr, container, false);
        // XML association
        TextInputEditText mFileName = view.findViewById(R.id.workout_name);
        TextView maxHRview = view.findViewById(R.id.max_hr_view);
        TextView maxVview = view.findViewById(R.id.max_v_view);
        Spinner mMaxHR = view.findViewById(R.id.max_hr_spinner);
        Spinner mMaxV = view.findViewById(R.id.max_v_spinner);
        Spinner mDuration = view.findViewById(R.id.duration_spinner);
        Spinner mZone = view.findViewById(R.id.zone_spinner);
        Spinner mIncl = view.findViewById(R.id.incl_spinner);
        Button mMaxHRentry = view.findViewById(R.id.max_hr_btn);
        Button mEntry = view.findViewById(R.id.add_step_btn);
        RecyclerView mRecyclerView = view.findViewById(R.id.recycler_view);
        // Object constructor
        WorkoutObject workout = new WorkoutObject();
        /*
        ArrayList<String> durList = new ArrayList<>();
        ArrayList<String> speedList = new ArrayList<>();
        ArrayList<String> inclList = new ArrayList<>();
        double incrDur = 0.5;
        int durRange = 30;
        for (int i = 0; i < (int) (durRange); i++) {
            durList.add(String.format("%.1f%",i*incrDur+incrDur));
        }

        double incrSpeed = RunActivity.getSpeedIncrement();
        int speedRange = (int) (RunActivity.getMaxSpeed() / incrSpeed);
        for (int j = 0; j < speedRange; j++) {
            speedList.add(String.format("%.1f%",j*incrSpeed+incrSpeed));
        }

        double incrIncl = RunActivity.getInclIncrement();
        int InclRange = (int) (RunActivity.getMaxIncl() / incrIncl);
        for (int k = 0; k < InclRange; k++) {
            inclList.add(String.format("%.1f%",k*incrIncl));
        */
        // Spinner options lists
        ArrayList<Float> durList = new ArrayList<>();
        ArrayList<Float> maxHRList = new ArrayList<>();
        ArrayList<Float> maxVList = new ArrayList<>();
        ArrayList<Float> zoneList = new ArrayList<>();
        ArrayList<Float> inclList = new ArrayList<>();

        float incrDur = 0.5F;
        int durMin = 0;
        int durMax = 30;
        for (int i = 0; i <= (durMax-durMin)/incrDur; i++) {
            durList.add(durMin+i*incrDur);
        }

        float incrZone = 1;
        int ZoneMin = 1;
        int ZoneMax = 4;
        for (int i = 0; i <= (ZoneMax-ZoneMin)/incrZone; i++) {
            zoneList.add(ZoneMin+i*incrZone);
        }

        float incrIncl = 0.5F;
        int inclMin = 0;
        int inclMax = 20;
        for (int i = 0; i <= (inclMax-inclMin)/incrIncl; i++) {
            inclList.add(inclMin+i*incrIncl);
        }

        float incrHR = 5;
        int HrMin = 150;
        int HrMax = 250;
        for (int i = 0; i <= (HrMax-HrMin)/incrHR; i++) {
            maxHRList.add(HrMin+i*incrHR);
        }

        float incrV = 0.5F;
        int Vmin = 12;
        int Vmax = 25;
        for (int i = 0; i <= (Vmax-Vmin)/incrV; i++) {
            maxVList.add(Vmin+i*incrV);
        }

        // Set Spinner Adapters
        ArrayAdapter maxHRAdapter =
                new ArrayAdapter(getActivity(), android.R.layout.simple_spinner_item,maxHRList);
        ArrayAdapter maxVAdapter =
                new ArrayAdapter(getActivity(),android.R.layout.simple_spinner_item,maxVList);
        ArrayAdapter durAdapter =
                new ArrayAdapter(getActivity(),android.R.layout.simple_spinner_item,durList);
        ArrayAdapter zoneAdapter =
                new ArrayAdapter(getActivity(),android.R.layout.simple_spinner_item,zoneList);
        ArrayAdapter inclAdapter =
                new ArrayAdapter(getActivity(),android.R.layout.simple_spinner_item,inclList);

        maxHRAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maxVAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        durAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        inclAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        mMaxHR.setAdapter(maxHRAdapter);
        mMaxV.setAdapter(maxVAdapter);
        mDuration.setAdapter(durAdapter);
        mZone.setAdapter(zoneAdapter);
        mIncl.setAdapter(inclAdapter);

        // Spinner Listeners
        mMaxHR.setOnItemSelectedListener(this);
        mMaxV.setOnItemSelectedListener(this);
        mDuration.setOnItemSelectedListener(this);
        mZone.setOnItemSelectedListener(this);
        mIncl.setOnItemSelectedListener(this);

        // RecyclerView Adapter
        WorkoutAdapter mAdapter = new WorkoutAdapter(view.getContext(), workout);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));

        mEntry.setOnClickListener(v -> {
            workout.setDurList(durEntry);
            workout.setZoneList(zoneEntry);
            workout.setInclList(inclEntry);
            int workoutSize = workout.getDurList().size();
            mRecyclerView.getAdapter().notifyItemInserted(workoutSize+1);
            // Scroll to the bottom.
            mRecyclerView.smoothScrollToPosition(workoutSize);
        });

        mMaxHRentry.setOnClickListener(v -> {
            workout.setMaxHR(maxHREntry);
            workout.setMaxV(maxVEntry);
            maxHRview.setText(workout.getMaxHR());
            maxVview.setText(workout.getMaxV());
        });
        /*
        TODO! Find a more efficient way to delete unwanted entries
        mDelEntry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                entryList.remove(entry);
            }
        });
         */
        return view;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(parent.getId() == R.id.max_hr_spinner) maxHREntry = (float) parent.getSelectedItem();
        if(parent.getId() == R.id.max_v_spinner) maxVEntry = (float) parent.getSelectedItem();
        if(parent.getId() == R.id.duration_spinner) durEntry = (float) parent.getSelectedItem();
        if(parent.getId() == R.id.zone_spinner) zoneEntry = (float) parent.getSelectedItem();
        if(parent.getId() == R.id.incl_spinner) inclEntry = (float) parent.getSelectedItem();

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}
