package org.appspot.apprtc.fragment;

import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.appspot.apprtc.ChatAdapter;
import org.appspot.apprtc.ChatListAdapter;
import org.appspot.apprtc.FileAdapter;
import org.appspot.apprtc.R;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.User;

import java.io.File;
import java.util.ArrayList;


public class FilesFragment extends Fragment {
    private View controlView;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private TextView emptyFiles;

    public FilesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        controlView = inflater.inflate(R.layout.fragment_files, container, false);

        recyclerView= (RecyclerView) controlView.findViewById(R.id.recycler_view);
        emptyFiles = (TextView) controlView.findViewById(R.id.emptyFiles);

        return controlView;
    }

    @Override
    public void onStart() {
        super.onStart();

        layoutManager=new LinearLayoutManager(controlView.getContext());
        recyclerView.setLayoutManager(layoutManager);

        File[] files = new File(Environment.getExternalStorageDirectory() + "/Download").listFiles();
        adapter = new FileAdapter(files, controlView.getContext());

        recyclerView.setAdapter(adapter);

        if (files != null && files.length != 0) {
            emptyFiles.setVisibility(View.GONE);
        }
    }

}
