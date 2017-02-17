package org.appspot.apprtc;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UsersViewHolder> {

    ArrayList<String> planetList;

    public UsersAdapter(ArrayList<String> planetList, Context context) {
        this.planetList = planetList;
    }

    @Override
    public UsersAdapter.UsersViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v= LayoutInflater.from(parent.getContext()).inflate(R.layout.user_row,parent,false);
        UsersViewHolder viewHolder=new UsersViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(UsersAdapter.UsersViewHolder holder, int position) {
        holder.image.setImageResource(R.drawable.user_icon);
        holder.text.setText(planetList.get(position).toString());
    }

    @Override
    public int getItemCount() {
        return planetList.size();
    }

    public static class UsersViewHolder extends RecyclerView.ViewHolder{

        protected ImageView image;
        protected TextView text;

        public UsersViewHolder(View itemView) {
            super(itemView);
            image= (ImageView) itemView.findViewById(R.id.image_id);
            text= (TextView) itemView.findViewById(R.id.text_id);
        }
    }
}