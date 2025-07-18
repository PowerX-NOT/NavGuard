package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayDeque;
import java.util.Arrays;

public class EmergencyTerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;
    private LocationManager locationManager;

    private TextView receiveText;
    private EditText sendText;
    private Button sendBtn;
    private Button emergencyBtn;
    private Button sosBtn;
    private TextView statusText;
    private TextView locationText;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
    
    private Handler sosHandler = new Handler();
    private Runnable sosRunnable;
    private boolean sosPressed = false;
    private static final int SOS_HOLD_TIME = 5000; // 5 seconds
    
    private Vibrator vibrator;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        locationManager = new LocationManager(getContext());
        vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        locationManager.stopLocationUpdates();
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class));
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_emergency_terminal, container, false);
        
        receiveText = view.findViewById(R.id.receive_text);
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText));
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        sendText.setHint("Type emergency message...");

        sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> sendRegularMessage(sendText.getText().toString()));
        
        emergencyBtn = view.findViewById(R.id.emergency_btn);
        emergencyBtn.setOnClickListener(v -> sendEmergencyMessage(sendText.getText().toString()));
        
        sosBtn = view.findViewById(R.id.sos_btn);
        setupSosButton();
        
        statusText = view.findViewById(R.id.status_text);
        locationText = view.findViewById(R.id.location_text);
        
        updateStatus("Initializing...");
        
        return view;
    }
    
    private void setupSosButton() {
        sosBtn.setOnLongClickListener(v -> {
            sosPressed = true;
            sosBtn.setText("Hold for SOS...");
            sosBtn.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
            
            sosRunnable = () -> {
                if (sosPressed) {
                    triggerSosAlert();
                }
            };
            sosHandler.postDelayed(sosRunnable, SOS_HOLD_TIME);
            return true;
        });
        
        sosBtn.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                sosPressed = false;
                sosBtn.setText("SOS");
                sosBtn.setBackgroundColor(getResources().getColor(R.color.colorAccent));
                if (sosRunnable != null) {
                    sosHandler.removeCallbacks(sosRunnable);
                }
            }
            return false;
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_emergency_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.device_status) {
            showDeviceStatus();
            return true;
        } else if (id == R.id.emergency_contacts) {
            showEmergencyContacts();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Emergency Functions
     */
    private void sendRegularMessage(String message) {
        if (message.trim().isEmpty()) {
            Toast.makeText(getActivity(), "Please enter a message", Toast.LENGTH_SHORT).show();
            return;
        }
        
        EmergencyMessage emergencyMessage = new EmergencyMessage(message, EmergencyMessage.MessageType.REGULAR);
        sendMessage(emergencyMessage);
    }
    
    private void sendEmergencyMessage(String message) {
        if (message.trim().isEmpty()) {
            message = "EMERGENCY: Need immediate assistance!";
        }
        
        // Get current location for emergency message
        locationManager.getCurrentLocation(new LocationManager.LocationCallback() {
            @Override
            public void onLocationReceived(double latitude, double longitude) {
                EmergencyMessage emergencyMessage = new EmergencyMessage(message, EmergencyMessage.MessageType.EMERGENCY, latitude, longitude);
                sendMessage(emergencyMessage);
                updateLocationDisplay(latitude, longitude);
            }

            @Override
            public void onLocationError(String error) {
                // Send emergency message without location
                EmergencyMessage emergencyMessage = new EmergencyMessage(message, EmergencyMessage.MessageType.EMERGENCY);
                sendMessage(emergencyMessage);
                status("Emergency sent without GPS: " + error);
            }
        });
    }
    
    private void triggerSosAlert() {
        sosBtn.setText("SOS ACTIVATED");
        sosBtn.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
        
        // Vibrate to indicate SOS activation
        if (vibrator != null) {
            vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
        }
        
        status("🚨 SOS ALERT ACTIVATED 🚨");
        
        locationManager.getCurrentLocation(new LocationManager.LocationCallback() {
            @Override
            public void onLocationReceived(double latitude, double longitude) {
                String sosMessage = "🚨 SOS ALERT 🚨 Emergency assistance needed immediately!";
                EmergencyMessage sosMsg = new EmergencyMessage(sosMessage, EmergencyMessage.MessageType.SOS, latitude, longitude);
                sendMessage(sosMsg);
                updateLocationDisplay(latitude, longitude);
                status("SOS alert sent with GPS coordinates");
            }

            @Override
            public void onLocationError(String error) {
                String sosMessage = "🚨 SOS ALERT 🚨 Emergency assistance needed immediately!";
                EmergencyMessage sosMsg = new EmergencyMessage(sosMessage, EmergencyMessage.MessageType.SOS);
                sendMessage(sosMsg);
                status("SOS alert sent without GPS: " + error);
            }
        });
        
        // Reset SOS button after 3 seconds
        sosHandler.postDelayed(() -> {
            sosBtn.setText("SOS");
            sosBtn.setBackgroundColor(getResources().getColor(R.color.colorAccent));
        }, 3000);
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            updateStatus("Connecting to emergency device...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void sendMessage(EmergencyMessage message) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "Device not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Convert message to JSON or custom format for transmission
            String messageData = formatMessageForTransmission(message);
            byte[] data = (messageData + newline).getBytes();
            
            // Display in UI
            displayMessage(message, true);
            
            // Send via Bluetooth to LoRa device
            service.write(data);
            
            // Clear input field
            sendText.setText("");
            
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }
    
    private String formatMessageForTransmission(EmergencyMessage message) {
        // Simple format: TYPE|CONTENT|LAT|LON|TIMESTAMP
        StringBuilder sb = new StringBuilder();
        sb.append(message.getType().name()).append("|");
        sb.append(message.getContent()).append("|");
        sb.append(message.getLatitude()).append("|");
        sb.append(message.getLongitude()).append("|");
        sb.append(message.getTimestamp());
        return sb.toString();
    }
    
    private void displayMessage(EmergencyMessage message, boolean sent) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        
        String prefix = sent ? "SENT: " : "RECEIVED: ";
        spn.append(prefix).append(message.toString()).append('\n');
        
        int color;
        if (message.isEmergency()) {
            color = getResources().getColor(android.R.color.holo_red_dark);
        } else if (sent) {
            color = getResources().getColor(R.color.colorSendText);
        } else {
            color = getResources().getColor(R.color.colorRecieveText);
        }
        
        spn.setSpan(new ForegroundColorSpan(color), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
        
        // Scroll to bottom
        receiveText.post(() -> {
            receiveText.setSelection(receiveText.getText().length());
        });
    }

    private void receive(ArrayDeque<byte[]> datas) {
        for (byte[] data : datas) {
            String receivedData = new String(data);
            
            // Parse received message
            EmergencyMessage message = parseReceivedMessage(receivedData);
            if (message != null) {
                displayMessage(message, false);
                
                // Handle emergency messages
                if (message.isEmergency()) {
                    handleEmergencyMessage(message);
                }
                
                // If device is not paired (service not attached), sound buzzer
                if (service == null) {
                    soundBuzzer();
                }
            } else {
                // Display raw data if parsing fails
                SpannableStringBuilder spn = new SpannableStringBuilder("RAW: " + receivedData + '\n');
                receiveText.append(spn);
            }
        }
    }
    
    private EmergencyMessage parseReceivedMessage(String data) {
        try {
            String[] parts = data.trim().split("\\|");
            if (parts.length >= 5) {
                EmergencyMessage message = new EmergencyMessage();
                message.setType(EmergencyMessage.MessageType.valueOf(parts[0]));
                message.setContent(parts[1]);
                message.setLatitude(Double.parseDouble(parts[2]));
                message.setLongitude(Double.parseDouble(parts[3]));
                message.setTimestamp(Long.parseLong(parts[4]));
                return message;
            }
        } catch (Exception e) {
            // Parsing failed, return null
        }
        return null;
    }
    
    private void handleEmergencyMessage(EmergencyMessage message) {
        // Vibrate for emergency messages
        if (vibrator != null) {
            vibrator.vibrate(1000);
        }
        
        // Show emergency notification
        Toast.makeText(getActivity(), "🚨 EMERGENCY MESSAGE RECEIVED 🚨", Toast.LENGTH_LONG).show();
        
        // Update location if available
        if (message.hasLocation()) {
            updateLocationDisplay(message.getLatitude(), message.getLongitude());
        }
    }
    
    private void soundBuzzer() {
        // This would typically send a command to the Arduino to sound the buzzer
        // For now, we'll vibrate the phone as a substitute
        if (vibrator != null) {
            vibrator.vibrate(new long[]{0, 200, 100, 200, 100, 200}, -1);
        }
    }

    private void updateStatus(String message) {
        if (statusText != null) {
            statusText.setText("Status: " + message);
        }
    }
    
    private void updateLocationDisplay(double latitude, double longitude) {
        if (locationText != null) {
            String locationStr = String.format("GPS: %.6f, %.6f", latitude, longitude);
            locationText.setText(locationStr);
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }
    
    private void showDeviceStatus() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Device Status");
        
        StringBuilder status = new StringBuilder();
        status.append("Connection: ").append(connected == Connected.True ? "Connected" : "Disconnected").append("\n");
        status.append("GPS: ").append(hasLocationPermission() ? "Available" : "Permission needed").append("\n");
        status.append("Bluetooth: ").append(BluetoothAdapter.getDefaultAdapter().isEnabled() ? "Enabled" : "Disabled").append("\n");
        
        builder.setMessage(status.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    private void showEmergencyContacts() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Emergency Contacts");
        builder.setMessage("Configure emergency contacts and rescue team frequencies in device settings.");
        builder.setPositiveButton("OK", null);
        builder.show();
    }
    
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        updateStatus("Connected to emergency device");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        updateStatus("Connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        updateStatus("Connection lost: " + e.getMessage());
        disconnect();
    }
}