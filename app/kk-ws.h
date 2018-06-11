//
//  kk-ws.h
//  app
//
//  Created by hailong11 on 2018/6/8.
//  Copyright © 2018年 kkmofang.cn. All rights reserved.
//

#ifndef kk_ws_h
#define kk_ws_h

#include "kk-script.h"
#include <event.h>
#include <evhttp.h>
#include <evdns.h>

namespace kk {
    
    enum WebSocketState {
        WebSocketStateNone,WebSocketStateResolve,WebSocketStateConnected,WebSocketStateOpened,WebSocketStateClosed
    };
    
    enum WebSocketType {
        WebSocketType
    };
    
    class WebSocket : public Object {
    public:
        WebSocket();
        virtual ~WebSocket();
        
        virtual kk::Boolean open(event_base * base,evdns_base * dns, kk::CString url,kk::CString protocol);
        virtual void close();
        virtual void send(void * data,size_t n);
        virtual void send(kk::CString text);
        
        virtual duk_ret_t duk_on(duk_context * ctx);
        virtual duk_ret_t duk_close(duk_context * ctx);
        
        DEF_SCRIPT_CLASS
        
    protected:
        kk::String _errmsg;
        bufferevent * _bev;
        WebSocketState _state;
        struct sockaddr_in _addr;
        
        virtual void onClose(kk::CString errmsg);
        virtual void onResolve(struct in_addr * addr);
        virtual void onConnected();
        virtual void onWritting();
        virtual void onReading();
        virtual void onOpen();
        
        friend void WebSocket_data_rd(struct bufferevent *bev, void *ctx);
        friend void WebSocket_data_wd(struct bufferevent *bev, void *ctx);
        friend void WebSocket_event_cb(struct bufferevent *bev, short what, void *ctx);
        friend void WebSocket_evdns_cb (int result, char type, int count, int ttl, void *addresses, void *arg);
    };
    
}

#endif /* kk_ws_h */