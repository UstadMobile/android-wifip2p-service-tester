    package com.ustadmobile.wifiP2pServiceDiscovery;

    import android.content.Context;
    import android.content.IntentFilter;
    import android.net.wifi.p2p.WifiP2pConfig;
    import android.net.wifi.p2p.WifiP2pDevice;
    import android.net.wifi.p2p.WifiP2pInfo;
    import android.net.wifi.p2p.WifiP2pManager;
    import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
    import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
    import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
    import android.os.Build;
    import android.os.Bundle;
    import android.os.CountDownTimer;
    import android.os.Handler;
    import android.support.v7.app.AppCompatActivity;
    import android.support.v7.widget.AppCompatEditText;
    import android.support.v7.widget.LinearLayoutManager;
    import android.support.v7.widget.RecyclerView;
    import android.support.v7.widget.SwitchCompat;
    import android.util.Log;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.view.ViewGroup;
    import android.widget.CompoundButton;
    import android.widget.LinearLayout;
    import android.widget.TextView;
    import android.widget.Toast;
    import java.text.DateFormat;
    import java.text.SimpleDateFormat;
    import java.util.ArrayList;
    import java.util.Date;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Locale;
    import java.util.Map;
    import java.util.WeakHashMap;

    public class MainActivity extends AppCompatActivity implements WifiP2pManager.ConnectionInfoListener {

        public static final String TAG = "DemoProject";
        public static final String TXTRECORD_PROP_AVAILABLE = "available";
        public static final String SERVICE_INSTANCE = "ustadDemo";
        public static final String SERVICE_REG_TYPE = "ustadDemo._tcp";
        public static final String SERVICE_REQUEST = "request";
        public static final String FULL_DOMAIN=SERVICE_INSTANCE+"."+SERVICE_REG_TYPE+".local.";
        private static final String DEVICE_NAMES = "deviceName";
        private static final String DEVICE_MAC = "deviceAddress";
        private static final String DEVICE_STATUS = "deviceStatus";
        private static final String DEVICE_LAST_UPDATED = "deviceLastUpdated";
        private static final String DEVICE_LATENCY = "deviceLatency";
        private WiFiDirectBroadcastReceiver receiver;

        private WifiP2pManager manager;
        private final IntentFilter intentFilter = new IntentFilter();
        private WifiP2pManager.Channel channel;
        private Map<String,String> dataRecord;
        private WifiP2pServiceRequest mWifiP2pServiceRequest;
        private int SERVICE_DISCOVERING_INTERVAL=0;

        private static  int BROADCAST_DISCOVER_WAIT = 0;

        public static int SERVICE_BROADCASTING_INTERVAL = 0;

        private Handler mServiceDiscoveringHandler=new Handler();
        private Handler mServiceBroadcastingHandler = new Handler();

        private SwitchCompat broadcast, discover;

        private ArrayList<Device> deviceList;
        private  CountDownTimer mySystemTime;


        //The last time we updated the timestamp we are broadcasting
        private long lastServiceUpdateTime = 0;
        //Device recycler adapter
        private DeviceAdapter mDeviceAdapter;
        //Time modification fields
        private AppCompatEditText serviceDiscoveryTimeInterval,broadcastDiscoveryWaitTimeInterval,broadcastIntervalTime;
        //system time
        private TextView systemTime;
        //The amount of time between updating the timestamp
        private static final int BROADCAST_TIME_INTERVAL = 60000;
        //service broadcast thread
        private Runnable mServiceBroadcastingRunnable = new Runnable() {
            @Override
            public void run() {
                long now = new Date().getTime();
                if(now - lastServiceUpdateTime >= BROADCAST_TIME_INTERVAL) {
                    broadcastLocalService();
                }else {
                    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onFailure(int error) {
                        }
                    });
                    mServiceBroadcastingHandler
                            .postDelayed(mServiceBroadcastingRunnable, SERVICE_BROADCASTING_INTERVAL);
                }
            }
        };



        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            deviceList = new ArrayList<>();
            mDeviceAdapter = new DeviceAdapter(deviceList);

            setContentView(R.layout.activity_main);

            //start initialisation
            initialiseIntents();

            //layout initialization and binding
            broadcast=(SwitchCompat)findViewById(R.id.broadcast);
            discover=(SwitchCompat)findViewById(R.id.discover);
            RecyclerView allPeersAvailable = (RecyclerView) findViewById(R.id.allPeers);
            serviceDiscoveryTimeInterval= (AppCompatEditText) findViewById(R.id.serviceDescoveryTime);
            broadcastIntervalTime= (AppCompatEditText) findViewById(R.id.broadcastTime);
            broadcastDiscoveryWaitTimeInterval= (AppCompatEditText) findViewById(R.id.broadcastDiscoveryTime);
            systemTime= (TextView) findViewById(R.id.systemTime);

            //set peer adapter, device information
            allPeersAvailable.setAdapter(mDeviceAdapter);

            //get updates on the system time (Clock sync)
            getSystemTime();

            //RecyclerView layout manager for the devices
            final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            allPeersAvailable.setLayoutManager(layoutManager);


            //on broadcast button checked action listener
            broadcast.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {

                    if(checked){
                       if(broadcastDiscoveryWaitTimeInterval.getText().toString().length()>0 && broadcastIntervalTime.getText().toString().length()>0){

                           //assign time values
                           SERVICE_BROADCASTING_INTERVAL=Integer.parseInt(broadcastIntervalTime.getText().toString());
                           BROADCAST_DISCOVER_WAIT=Integer.parseInt(broadcastDiscoveryWaitTimeInterval.getText().toString());

                           //check your values
                           if(SERVICE_BROADCASTING_INTERVAL>0 && BROADCAST_DISCOVER_WAIT>0){
                               broadcastLocalService();
                               broadcastDiscoveryWaitTimeInterval.setEnabled(false);
                               broadcastIntervalTime.setEnabled(false);
                           }else{
                               broadcast.setChecked(false);
                               Toast.makeText(getApplicationContext(),"The values should be greater than 0",Toast.LENGTH_LONG).show();
                           }
                       }else{
                           broadcast.setChecked(false);
                           Toast.makeText(getApplicationContext(),"Please fill all the service broadcast and broadcast wait time",Toast.LENGTH_LONG).show();
                       }
                    }else {
                        //disable nad enable processes to normal after checking off.
                        broadcastDiscoveryWaitTimeInterval.setEnabled(true);
                        broadcastIntervalTime.setEnabled(true);
                        mServiceBroadcastingHandler.removeCallbacks(mServiceBroadcastingRunnable);
                    }
                }
            });

            //on service discovery button checked action listener
            discover.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    if(checked){
                        if(broadcastDiscoveryWaitTimeInterval.getText().toString().length()>0 && serviceDiscoveryTimeInterval.getText().length()>0){

                            SERVICE_DISCOVERING_INTERVAL=Integer.parseInt(serviceDiscoveryTimeInterval.getText().toString());
                            BROADCAST_DISCOVER_WAIT=Integer.parseInt(broadcastDiscoveryWaitTimeInterval.getText().toString());

                            if(SERVICE_BROADCASTING_INTERVAL>0 && BROADCAST_DISCOVER_WAIT>0){
                                prepareServiceDiscovery();
                                serviceDiscoveryTimeInterval.setEnabled(false);
                            }else{
                                discover.setChecked(false);
                                Toast.makeText(getApplicationContext(),"The values should be greater than 0",Toast.LENGTH_LONG).show();
                            }

                        }else{
                            discover.setChecked(false);
                            Toast.makeText(getApplicationContext(),"Please fill all the broadcast wait & service discovery time",Toast.LENGTH_LONG).show();
                        }
                    }else {
                        serviceDiscoveryTimeInterval.setEnabled(true);
                        mServiceDiscoveringHandler.removeCallbacks(mServiceDiscoveringRunnable);
                    }
                }
            });



        }



        void initialiseIntents(){
            //add actions to the intent filter
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
            //initialize WifiP2p manager and Channel.
            manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            channel = manager.initialize(this, getMainLooper(), null);

            loggingMessage(this.getClass().getName(),"initialiseIntents"," set actions on intent",false);
        }

        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {

            //get extra information when devices are connected

        }



        @Override
        protected void onStop() {
            //kill the group connections when exiting the app
            if (manager != null && channel != null) {
                manager.removeGroup(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onFailure(int reasonCode) {
                        loggingMessage(this.getClass().getName(),"onsTop: onFailure","Disconnect failed. Reason :" + reasonCode,true);
                    }

                    @Override
                    public void onSuccess() {
                        loggingMessage(this.getClass().getName(),"onsTop: onFailure","Disconnected successfully",false);
                    }

                });
            }
            super.onStop();
        }



        //create your service and broadcast it
        void broadcastLocalService(){
            if(Build.VERSION.SDK_INT>=16){
                manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        loggingMessage(this.getClass().getName(),"clearLocalServices: onSuccess","Services cleared successfully",false);
                        final String time=String.valueOf(new Date().getTime());
                        dataRecord=new HashMap<>();
                        dataRecord.put(TXTRECORD_PROP_AVAILABLE, "visible");
                        dataRecord.put(SERVICE_REQUEST, time);

                        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(SERVICE_INSTANCE, SERVICE_REG_TYPE, dataRecord);
                        manager.addLocalService(channel, service, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                lastServiceUpdateTime = new Date().getTime();
                                ((TextView)findViewById(R.id.lastTime)).setText(getCurrentTime(Long.parseLong(time)));
                                loggingMessage(this.getClass().getName(),"startRegistrationAndDiscovery: onSuccess","Local service added successfully "+time,false);
                                mServiceBroadcastingHandler.postDelayed(mServiceBroadcastingRunnable,
                                        BROADCAST_DISCOVER_WAIT);
                            }

                            @Override
                            public void onFailure(int error) {
                                loggingMessage(this.getClass().getName(),"startRegistrationAndDiscovery: onFailure","Local service was not added",true);
                            }
                        });



                    }

                    @Override
                    public void onFailure(int i) {
                        loggingMessage(this.getClass().getName(),"clearLocalServices: onFailure","Service was not cleared successfully",true);
                    }
                });
            }
        }


        //prepare for service discovery
        public void prepareServiceDiscovery() {
            loggingMessage(this.getClass().getName(),"prepareServiceDiscovery ","Prepare for discovery",false);
            mWifiP2pServiceRequest = WifiP2pDnsSdServiceRequest.newInstance();

            manager.setDnsSdResponseListeners(channel,
                    new WifiP2pManager.DnsSdServiceResponseListener() {

                        @Override
                        public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice srcDevice) {

                            loggingMessage(this.getClass().getName(),"prepareServiceDiscovery: onDnsSdServiceAvailable","Device Information detected",false);

                        }
                    }, new WifiP2pManager.DnsSdTxtRecordListener() {

                        @Override
                        public void onDnsSdTxtRecordAvailable(String fullDomainName, Map<String, String> record, WifiP2pDevice device) {


                            loggingMessage(this.getClass().getName(),"prepareServiceDiscovery: onDnsSdTxtRecordAvailable","Device Information Found",false);
                            loggingMessage(this.getClass().getName(),"prepareServiceDiscovery: onDnsSdTxtRecordAvailable","Device Information Found :Name ="+
                                    device.deviceName+", Address = "+device.deviceAddress+", Time = "+record.get(SERVICE_REQUEST),false);

                            /*

                            if you need to filter out the services

                            if(fullDomainName.equalsIgnoreCase(FULL_DOMAIN)){

                                everything goes here
                            }

                            */

                            Device deviceInList = new Device(device.deviceAddress);
                            int deviceIndex = deviceList.indexOf(deviceInList);
                            if(deviceIndex != -1) {
                                deviceInList = deviceList.get(deviceIndex);
                            }else {
                                deviceList.add(deviceInList);
                            }


                            Map<String,String> deviceInstance = deviceInList.getAttrs();
                            String lastTimeKnown = deviceInstance.get(SERVICE_REQUEST);
                            String timeInRecord = record.get(SERVICE_REQUEST);
                            Long latency;

                            if(lastTimeKnown == null || !lastTimeKnown.equals(timeInRecord)) {
                                deviceInstance.put(DEVICE_LAST_UPDATED, timeInRecord);
                                latency = new Date().getTime() - Long.parseLong(timeInRecord);
                                deviceInstance.put(DEVICE_LATENCY, String.valueOf(latency));
                            }


                            deviceInstance.put(DEVICE_NAMES,device.deviceName);
                            deviceInstance.put(DEVICE_MAC,device.deviceAddress);
                            deviceInstance.put(DEVICE_STATUS,String.valueOf(device.status));
                            deviceInstance.put(SERVICE_REQUEST,record.get(SERVICE_REQUEST));

                            mDeviceAdapter.updateDeviceById(device.deviceAddress);
                            mDeviceAdapter.notifyDataSetChanged();
                        }
                    });

            startServiceDiscovery();
        }


        //get your time in a formatted way.
        private String getCurrentTime(Long longTime){
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ENGLISH);
            Date date = new Date(longTime);
            return dateFormat.format(date);
        }


        //update your view from the system time
        private void getSystemTime(){
           mySystemTime = new CountDownTimer(1000000000, 1000) {

                public void onTick(long millisUntilFinished) {
                    systemTime.setText(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).format(new Date()));
                }
                public void onFinish() {

                }
            };
            mySystemTime.start();
        }


        //start your discovery process
        private void startServiceDiscovery() {
            manager.removeServiceRequest(channel, mWifiP2pServiceRequest,
                    new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            manager.addServiceRequest(channel, mWifiP2pServiceRequest, new WifiP2pManager.ActionListener() {


                                @Override
                                public void onSuccess() {
                                    manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
                                        @Override
                                        public void onSuccess() {
                                            loggingMessage(this.getClass().getName(), "startServiceDiscovery: onSuccess :onSuccess: onSuccess", "Service discovery started", false);
                                            mServiceDiscoveringHandler.postDelayed(mServiceDiscoveringRunnable, SERVICE_DISCOVERING_INTERVAL);

                                        }

                                        @Override
                                        public void onFailure(int error) {
                                            loggingMessage(this.getClass().getName(), "startServiceDiscovery: onSuccess :onSuccess: onFailure", "Service discovery failed to start", true);

                                        }
                                    });
                                }

                                @Override
                                public void onFailure(int error) {
                                    loggingMessage(this.getClass().getName(), "startServiceDiscovery: onSuccess :onFailure", "Failed to add service request", true);

                                }
                            });
                        }

                        @Override
                        public void onFailure(int reason) {

                            loggingMessage(this.getClass().getName(), "startServiceDiscovery: onFailure ", "Failed to remove service request", true);

                        }
                    });
        }


        //service discovery thread
        private Runnable mServiceDiscoveringRunnable = new Runnable() {
            @Override
            public void run() {
                loggingMessage(this.getClass().getName(),"runnable:","Starting service discovery",false);
                startServiceDiscovery();
            }
        };

        //log messages TRUE for Error messages and FALSE for debugging info
        static void loggingMessage(String className,String method,String message,boolean isError){
            if(isError){
                Log.e(TAG,"LoggingFunc"+className+":"+method+": "+message);
            }else{
                Log.d(TAG,"LoggingFunc"+className+":"+method+": "+message);
            }
        }


    //Device information adapter
        class DeviceAdapter extends RecyclerView.Adapter<DeviceHolder> {
            List<Device> allDevices;
            Map<String, DeviceHolder> holdersMap;


            DeviceAdapter(List<Device> allDevices) {
                this.allDevices=allDevices;
                holdersMap = new WeakHashMap<>();
            }

            @Override
            public DeviceHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return new DeviceHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.single_device, null));
            }

            @Override
            public void onBindViewHolder(final DeviceHolder holder, int position) {
                Device device = allDevices.get(position);
                if(holdersMap.containsValue(holder)) {
                    holdersMap.remove(holder.deviceId);
                }

                final HashMap<String, String> data = allDevices.get(position).getAttrs();
                holder.title.setText(allDevices.get(position).getAttrs().get(DEVICE_NAMES));

                holder.description.setText("Last Broadcast: " + getCurrentTime(
                        Long.parseLong(data.get(SERVICE_REQUEST))) + "\nAddress: " + data.get(DEVICE_MAC)
                        + "\nStatus: " + getDeviceStatus(Integer.parseInt(data.get(DEVICE_STATUS)))
                        +"\nLatency: "+ data.get(DEVICE_LATENCY) + "ms");

                holder.deviceId = device.getDeviceId();
                holdersMap.put(allDevices.get(position).getDeviceId(), holder);


                //request connection on device info item click

                holder.viewHolder.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        WifiP2pConfig configuration=new WifiP2pConfig();
                        configuration.deviceAddress=allDevices.get(holder.getAdapterPosition()).getAttrs().get(DEVICE_MAC);
                        connectDevice(configuration,allDevices.get(holder.getAdapterPosition()).getAttrs().get(DEVICE_NAMES));

                    }
                });
            }


        //connect device, with the configuration details.
            void connectDevice(WifiP2pConfig config, final String deviceName){
                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        loggingMessage(this.getClass().getName(), "connectDevice: onSuccess :", deviceName+ "connected successfully", false);

                    }

                    @Override
                    public void onFailure(int i) {
                        loggingMessage(this.getClass().getName(), "connectDevice: onFailure :", deviceName+ "connection failed", false);
                    }
                });


            }
            void updateDeviceById(String id) {
                DeviceHolder holder = holdersMap.get(id);

            }

            @Override
            public int getItemCount() {
                return allDevices.size();
            }
        }

        //get the right device status
        public static String getDeviceStatus(int statusCode) {
            switch (statusCode) {
                case WifiP2pDevice.CONNECTED:
                    return "Connected";
                case WifiP2pDevice.INVITED:
                    return "Invited";
                case WifiP2pDevice.FAILED:
                    return "Failed";
                case WifiP2pDevice.AVAILABLE:
                    return "Available";
                case WifiP2pDevice.UNAVAILABLE:
                    return "Unavailable";
                default:
                    return "Unknown";

            }
        }

        @Override
        public void onResume() {
            super.onResume();
            //register broadcast receiver for listening device status change
            receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
            registerReceiver(receiver, intentFilter);
        }

        @Override
        public void onPause() {
            super.onPause();
            //unregister your receiver
            unregisterReceiver(receiver);
        }

        //Device information holder
        class DeviceHolder extends RecyclerView.ViewHolder {
            TextView title, description;
            LinearLayout viewHolder;
            String deviceId;
            DeviceHolder(View itemView) {
                super(itemView);
                title = (TextView) itemView.findViewById(R.id.name);
                description = (TextView) itemView.findViewById(R.id.description);
                viewHolder= (LinearLayout) itemView.findViewById(R.id.viewHolder);
            }
        }

        //on back button pressed
        @Override
        public void onBackPressed() {
            super.onBackPressed();
            mySystemTime.cancel();
        }
    }
