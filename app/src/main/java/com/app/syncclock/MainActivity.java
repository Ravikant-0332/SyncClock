package com.app.syncclock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.app.syncclock.databinding.ActivityMainBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.checkerframework.checker.units.qual.C;

import java.time.Clock;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private ActivityMainBinding binding;
    private final DatabaseReference reference = FirebaseDatabase.getInstance().getReference();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mAuth = FirebaseAuth.getInstance();
        authenticateUser();

        new Handler().postDelayed(() -> binding.loader.setVisibility(View.GONE),1500);

    }

    @Override
    protected void onStart() {
        super.onStart();

        if(mAuth.getCurrentUser()!=null)
            stateListener();
        handleButtonClick();

    }

    private void handleButtonClick() {
        // Share Button Click listener
        binding.btnShareId.setOnClickListener(view ->startIntentForShare((String) binding.uid.getText()));
        // Sync Button Click listener
        binding.btnStartSync.setOnClickListener(view -> manageSyncState());

        binding.btnStartClock.setOnClickListener(view -> startClock());
        binding.btnStopClock.setOnClickListener(view -> stopClock());
    }

    @SuppressLint("SetTextI18n")
    private void startClock() {

        binding.tvTimeInfo.setText("Calculating...");

        String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        reference.child("users").get().addOnCompleteListener(task -> {
            if(task.isSuccessful()){
                CustomUser user = task.getResult().child(uid).getValue(CustomUser.class);
                if (user != null) {
                    String clockId = user.getClockId();
                    reference.child("clocks").child(clockId).child("time").setValue(String.valueOf(getSystemTime()));
                    reference.child("clocks").child(clockId).child("state").setValue(CustomClock.RUNNING);
                }
            }
        });
    }

    private void stopClock() {
        String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        reference.child("users").get().addOnCompleteListener(task -> {
            if(task.isSuccessful()){
                CustomUser user = task.getResult().child(uid).getValue(CustomUser.class);
                if (user != null) {
                    String clockId = user.getClockId();
                    reference.child("clocks").child(clockId).get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DataSnapshot> task) {
                            if (task.isSuccessful()){

                                DataSnapshot snapshot = Objects.requireNonNull(task.getResult());

                                long stopTime = getSystemTime();
                                long startTime = Long.parseLong(Objects.requireNonNull(snapshot.child("time").getValue(String.class)));
                                reference.child("clocks").child(clockId).child("state").setValue(CustomClock.IDLE);

                                long netTime = 0;
                                netTime = stopTime-startTime;
                                long hours, minutes, seconds;
                                hours = netTime/(36_00_000);
                                netTime%=36_00_000;
                                minutes = netTime/(60_000);
                                netTime%=60_000;
                                seconds = netTime/(1000);
                                netTime%=1000;
                                binding.tvTimeInfo.setText(String.format("%s Hours, %s Minutes, %s Seconds::%s", hours, minutes, seconds, netTime));
                            }
                        }
                    });
                }
            }
        });
    }

    private void manageSyncState() {

        if (binding.btnStartSync.getText().toString().equals("Sync")) {
            String id = binding.inputSyncClockId.getText().toString();
            if (Objects.requireNonNull(mAuth.getCurrentUser()).getUid().equals(id)) {
                Toast.makeText(this, "Can't Sync with Self", Toast.LENGTH_SHORT).show();
                return;
            }

            reference.child("users").get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DataSnapshot snapshot = task.getResult();
                    if (id.trim().length() != 0 && snapshot.hasChild(id)) {
                        CustomUser user = snapshot.child(id).getValue(CustomUser.class);
                        if (user != null && user.getConnectedWith().equals("")) {
                            setSyncButtonState(2, "");
                            syncUsers(id);
//                            Toast.makeText(MainActivity.this, "User found to sync", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "User is already in sync with another user", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Invalid Clock id", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }else{
            String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
            unSyncUsers(uid);
        }
    }

    private void stateListener(){
        reference.child("users").child(Objects.requireNonNull(mAuth.getCurrentUser()).getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                checkSyncState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        clockStateListener();

    }

    private void clockStateListener(){
        String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        reference.child("users").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                CustomUser user = task.getResult().child(uid).getValue(CustomUser.class);
                if (user != null && !user.getClockId().equals("")) {
                    reference.child("clocks").child(user.getClockId()).addValueEventListener(new ValueEventListener() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            int state;
                            state = Objects.requireNonNull(snapshot.child("state").getValue(Integer.class));
                            if (state == CustomClock.RUNNING){
                                binding.tvTimeInfo.setText("Calculating...");
                                disableButton(binding.btnStartClock);
                                enableButton(binding.btnStopClock);
                            }else{
                                enableButton(binding.btnStartClock);
                                disableButton(binding.btnStopClock);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });
                }
            }
        });
    }

    private void syncUsers(String id){
        String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        CustomClock clk = getClock();
        if(clk==null){
            Toast.makeText(this, "No Clock found at this time.", Toast.LENGTH_SHORT).show();
            return;
        }

        clk.setUid1(uid);
        clk.setUid2(id);
        clk.setAvailability(false);

        CustomUser u1 = new CustomUser();
        CustomUser u2 = new CustomUser();
        u1.setUid(uid);
        u1.setConnectedWith(id);
        u1.setClockId(clk.getId());
        u2.setUid(id);
        u2.setConnectedWith(uid);
        u2.setClockId(clk.getId());

        reference.child("clocks").child(clk.getId()).setValue(clk);

        reference.child("users").child(uid).setValue(u1).addOnCompleteListener(task -> {
            if(!task.isSuccessful()){
                Toast.makeText(MainActivity.this, Objects.requireNonNull(task.getException()).getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                setSyncButtonState(1,"");
                return;
            }
            reference.child("users").child(id).setValue(u2).addOnCompleteListener(task1 -> {
                if(!task1.isSuccessful()){
                    Toast.makeText(MainActivity.this, Objects.requireNonNull(task1.getException()).getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    setSyncButtonState(1,"");
                    return;
                }
                setSyncButtonState(3, id);
            });
        });
    }

    private void unSyncUsers(String uid){
        CustomClock clk = new CustomClock();
        CustomUser u1 = new CustomUser();
        CustomUser u2 = new CustomUser();
        u1.setUid(uid);
        u1.setConnectedWith("");
        u1.setClockId("");

        reference.child("users").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                CustomUser user = task.getResult().child(uid).getValue(CustomUser.class);
                if(user!=null){
                    u2.setUid(user.getConnectedWith());
                    u2.setConnectedWith("");
                    u2.setClockId("");
                    clk.setId(user.getClockId());
                    setSyncButtonState(4,u2.getUid());
                }
            }
        });

            reference.child("users").child(uid).setValue(u1).addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Toast.makeText(MainActivity.this, Objects.requireNonNull(task.getException()).getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                    setSyncButtonState(3, u2.getUid());
                    return;
                }
                reference.child("users").child(u2.getUid()).setValue(u2).addOnCompleteListener(task1 -> {
                    if (!task1.isSuccessful()) {
                        Toast.makeText(MainActivity.this, Objects.requireNonNull(task1.getException()).getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        setSyncButtonState(3, u2.getUid());
                        return;
                    }
                    setSyncButtonState(1, "");
                    freeClock(clk);
                });
            });
        setSyncButtonState(3, u2.getUid());
    }

    private void freeClock(CustomClock clk){
        reference.child("clocks").child(clk.getId()).setValue(null);
    }

    private CustomClock getClock(){
        String clockId = reference.child("clocks").push().getKey();

        if (clockId != null) {
            CustomClock clock = new CustomClock();
            clock.setId(clockId);
            reference.child("clocks").child(clockId).setValue(clock);
            return clock;
        }
        return null;
    }

    private void checkSyncState(){
        binding.syncProgressbar.setVisibility(View.VISIBLE);
        String id = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
        reference.child("users").get().addOnCompleteListener(task -> {
            if (task.isSuccessful()){
                DataSnapshot snapshot = task.getResult();
                CustomUser user = snapshot.child(id).getValue(CustomUser.class);

                if (user != null && user.getConnectedWith().equals("")) {
                    setSyncButtonState(1,"");
                }else{
                    if (user != null) {
                        setSyncButtonState(3, user.getConnectedWith());
                    }
                }
            }
            binding.syncProgressbar.setVisibility(View.INVISIBLE);
        });
    }

    @SuppressLint("SetTextI18n")
    private void setSyncButtonState(int state, String text){
        switch (state){
            case 1:{
                binding.btnStartSync.setText("Sync");
                binding.tvSyncStateInfo.setText("Not Synced");
                binding.syncProgressbar.setVisibility(View.INVISIBLE);
                enableButton(binding.btnStartSync);
                disableButton(binding.btnStartClock);
                disableButton(binding.btnStopClock);
                binding.inputSyncClockId.setText("");
                break;
            }
            case 2:{
                binding.btnStartSync.setText("Syncing...");
                binding.tvSyncStateInfo.setText("Not Synced");
                binding.syncProgressbar.setVisibility(View.VISIBLE);
                disableButton(binding.btnStartSync);
                disableButton(binding.btnStartClock);
                disableButton(binding.btnStopClock);
                break;
            }
            case 3:{
                binding.btnStartSync.setText("UnSync");
                binding.tvSyncStateInfo.setText(String.format("Clock is in Sync with: %s", text));
                binding.syncProgressbar.setVisibility(View.INVISIBLE);
                enableButton(binding.btnStartSync);
                enableButton(binding.btnStartClock);
                enableButton(binding.btnStopClock);
                binding.inputSyncClockId.setText("");
                break;
            }
            case 4:{
                binding.btnStartSync.setText("UnSyncing...");
                binding.tvSyncStateInfo.setText(String.format("Clock is in Sync with: %s", text));
                binding.syncProgressbar.setVisibility(View.VISIBLE);
                disableButton(binding.btnStartSync);
                disableButton(binding.btnStartClock);
                disableButton(binding.btnStopClock);
                break;
            }
            default:
        }
    }

    private void startIntentForShare(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Event Key");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "Share Clock ID Via"));
    }

    private void authenticateUser(){
        if(mAuth.getCurrentUser()==null){
            binding.userAuthLoader.setVisibility(View.VISIBLE);
            binding.homeLayout.setVisibility(View.INVISIBLE);
            signInAnonymously(mAuth);
        }else{
            binding.uid.setText(mAuth.getCurrentUser().getUid());
        }
    }

    private void signInAnonymously(FirebaseAuth mAuth) {
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();
                        // Sign in success, update UI with the signed-in user's information
                        binding.userAuthLoader.setVisibility(View.INVISIBLE);
                        binding.homeLayout.setVisibility(View.VISIBLE);
                        binding.uid.setText(uid);

                        CustomUser user = new CustomUser();
                        user.setUid(uid);

                        reference.child("users").child(uid).setValue(user).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (!task.isSuccessful()){
                                    mAuth.signOut();
                                    finish();
                                }
                            }
                        });

                    } else {
                        // If sign in fails, display a message to the user.
                        Toast.makeText(getApplicationContext(), "Authentication failed.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void disableButton(AppCompatButton button){
        button.setAlpha(0.3f);
        button.setClickable(false);
    }

    private void enableButton(AppCompatButton button){
        button.setAlpha(1.0f);
        button.setClickable(true);
    }

    private long getSystemTime(){
        return System.currentTimeMillis();
    }
}