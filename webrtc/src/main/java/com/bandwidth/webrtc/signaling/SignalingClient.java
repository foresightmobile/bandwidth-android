package com.bandwidth.webrtc.signaling;

import com.bandwidth.webrtc.signaling.listeners.OnConnectListener;
import com.bandwidth.webrtc.signaling.listeners.OnDisconnectListener;
import com.bandwidth.webrtc.signaling.listeners.OnOfferSdpListener;
import com.bandwidth.webrtc.signaling.listeners.OnRequestToPublishListener;
import com.bandwidth.webrtc.signaling.listeners.OnSetMediaPreferencesListener;
import com.bandwidth.webrtc.signaling.rpc.QueueRequest;
import com.bandwidth.webrtc.signaling.rpc.transit.AddIceCandidateParams;
import com.bandwidth.webrtc.signaling.rpc.transit.EndpointRemovedParams;
import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpParams;
import com.bandwidth.webrtc.signaling.rpc.transit.OfferSdpResult;
import com.bandwidth.webrtc.signaling.rpc.transit.RequestToPublishParams;
import com.bandwidth.webrtc.signaling.rpc.transit.RequestToPublishResult;
import com.bandwidth.webrtc.signaling.rpc.transit.SdpNeededParams;
import com.bandwidth.webrtc.signaling.rpc.transit.SetMediaPreferencesParams;
import com.bandwidth.webrtc.signaling.rpc.transit.base.Notification;
import com.bandwidth.webrtc.signaling.rpc.transit.base.Request;
import com.bandwidth.webrtc.signaling.rpc.transit.base.Response;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

@ClientEndpoint
public class SignalingClient implements Signaling {
    private SignalingDelegate delegate;

    private Session session;
    private Map<String, QueueRequest> pendingQueueRequests = new HashMap<>();

    private OnConnectListener onConnectListener;
    private OnDisconnectListener onDisconnectListener;
    private OnOfferSdpListener onOfferSdpListener;
    private OnRequestToPublishListener onRequestToPublishListener;
    private OnSetMediaPreferencesListener onSetMediaPreferencesListener;

    public SignalingClient(SignalingDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setOnConnectListener(OnConnectListener listener) {
        onConnectListener = listener;
    }

    @Override
    public void setOnDisconnectListener(OnDisconnectListener listener) {
        onDisconnectListener = listener;
    }

    @Override
    public void setOnOfferSdpListener(OnOfferSdpListener listener) {
        onOfferSdpListener = listener;
    }

    @Override
    public void setOnRequestToPublishListener(OnRequestToPublishListener listener) {
        onRequestToPublishListener = listener;
    }

    @Override
    public void setOnSetMediaPreferencesListener(OnSetMediaPreferencesListener listener) {
        onSetMediaPreferencesListener = listener;
    }

    @Override
    public void connect(URI uri) throws IOException, ConnectionException {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            container.connectToServer(this, uri);
        } catch (DeploymentException e) {
            throw new ConnectionException(e);
        }
    }

    @Override
    public void disconnect() throws IOException, NullSessionException {
        if (session == null) {
            throw new NullSessionException("There is no available session, session is null.");
        }

        session.close();
    }

    @Override
    public void offerSdp(String endpointId, String sdp) throws NullSessionException {
        OfferSdpParams params = new OfferSdpParams(endpointId, sdp);
        Request request = new Request<>(UUID.randomUUID().toString(), "2.0", "offerSdp", params);

        sendRequest(request);
    }

    @Override
    public void requestToPublish(Boolean audio, Boolean video, String alias) throws NullSessionException {
        List<String> mediaTypes = new ArrayList<>();

        if (audio) {
            mediaTypes.add("AUDIO");
        }

        if (video) {
            mediaTypes.add("VIDEO");
        }

        RequestToPublishParams params = new RequestToPublishParams(mediaTypes, alias);
        Request<RequestToPublishParams> request = new Request<>(UUID.randomUUID().toString(), "2.0", "requestToPublish", params);

        sendRequest(request);
    }

    @Override
    public void setMediaPreferences() throws NullSessionException {
        SetMediaPreferencesParams params = new SetMediaPreferencesParams("WEB_RTC", "NONE", false);
        Request request = new Request<>(UUID.randomUUID().toString(), "2.0", "setMediaPreferences", params);

        sendRequest(request);
    }

    private void sendRequest(Request request) throws NullSessionException {
        sendRequest(request, 5000L);
    }

    private void sendRequest(Request request, Long timeout) throws NullSessionException {
        if (session == null) {
            throw new NullSessionException("There is no available session, session is null.");
        }

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                pendingQueueRequests.remove(request.getId());
                this.cancel();
            }
        }, timeout);

        // Keep a reference to our request as we wait for a response.
        pendingQueueRequests.put(request.getId(), new QueueRequest(request.getMethod(), timer));

        String json = new Gson().toJson(request);

        System.out.println(json);

        if (session != null) {
            // Send our request to the moon (or signaling server).
            session.getAsyncRemote().sendText(json);
        }
    }

    private void sendNotification(Notification notification) throws NullSessionException {
        if (session == null) {
            throw new NullSessionException("There is no available session, session is null.");
        }

        String json = new Gson().toJson(notification);

        System.out.println(json);

        // Send our notification to the moon (or signaling server).
        session.getAsyncRemote().sendText(json);
    }

    @OnOpen
    private void onOpen(Session session) {
        this.session = session;

        if (onConnectListener != null) {
            onConnectListener.onConnect(this);
        }
    }

    @OnClose
    private void onClose(Session session, CloseReason reason) {
        this.session = null;

        if (onDisconnectListener != null) {
            onDisconnectListener.onDisconnect(this);
        }
    }

    @OnMessage
    private void onMessage(String message) {
        System.out.println("INCOMING MESSAGE");
        System.out.println(message);

        // Determine if we're receiving a response or notification.
        Response response = new Gson().fromJson(message, Response.class);
        Notification notification = new Gson().fromJson(message, Notification.class);

        if (response != null) {
            handleResponse(message, response.getId());
        } else if (notification != null) {
            handleNotification(message, notification);
        }
    }

    private void handleResponse(String message, String id) {
        QueueRequest pendingQueueRequest = pendingQueueRequests.get(id);
        pendingQueueRequest.getTimer().cancel();

        pendingQueueRequests.remove(id);

        switch (pendingQueueRequest.getMethod()) {
            case "setMediaPreferences":
                if (onSetMediaPreferencesListener != null) {
                    onSetMediaPreferencesListener.onSetMediaPreferences(this);
                }
                break;
            case "requestToPublish":
                if (onRequestToPublishListener != null) {
                    Type requestToPublishResultType = new TypeToken<Response<RequestToPublishResult>>() {
                    }.getType();
                    Response<RequestToPublishResult> requestToPublishResponse = new Gson().fromJson(message, requestToPublishResultType);

                    onRequestToPublishListener.onRequestToPublish(this, requestToPublishResponse.getResult());
                }
                break;
            case "offerSdp":
                if (onOfferSdpListener != null) {
                    Type offerSdpResultType = new TypeToken<Response<OfferSdpResult>>() {
                    }.getType();
                    Response<OfferSdpResult> offerSdpResponse = new Gson().fromJson(message, offerSdpResultType);

                    onOfferSdpListener.onOfferSdp(this, offerSdpResponse.getResult());
                }
                break;
        }
    }

    private void handleNotification(String message, Notification notification) {
        switch (notification.getMethod()) {
            case "addIceCandidate":
                Type addIceCandidateNotificationType = new TypeToken<Notification<AddIceCandidateParams>>() { }.getType();
                Notification<AddIceCandidateParams> addIceCandidateNotification = new Gson().fromJson(message, addIceCandidateNotificationType);

                delegate.onAddIceCandidate(this, addIceCandidateNotification.getParams());
                break;
            case "endpointRemoved":
                Type endpointRemovedNotificationType = new TypeToken<Notification<EndpointRemovedParams>>() { }.getType();
                Notification<EndpointRemovedParams> endpointRemovedNotification = new Gson().fromJson(message, endpointRemovedNotificationType);

                delegate.onEndpointRemoved(this, endpointRemovedNotification.getParams());
                break;
            case "sdpNeeded":
                Type sdpNeededNotificationType = new TypeToken<Notification<SdpNeededParams>>() { }.getType();
                Notification<SdpNeededParams> sdpNeededNotification = new Gson().fromJson(message, sdpNeededNotificationType);

                delegate.onSdpNeeded(this, sdpNeededNotification.getParams());
                break;
        }
    }
}
