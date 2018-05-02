//
//  KKGLViewElement.m
//  KKGame
//
//  Created by zhanghailong on 2018/2/5.
//  Copyright © 2018年 kkmofang.cn. All rights reserved.
//

#import "KKGLViewElement.h"
#import "KKGLView.h"

#include "GLContext.h"
#include "kk-script.h"

@interface KKGLViewElement() {
    BOOL _loaded;
    dispatch_source_t _source;
}

@property(nonatomic,strong) KKGLContext * GLContext;
@property(nonatomic,assign,readonly) kk::GA::Element * element;

-(void) _emit:(NSString *)name event:(KKEvent *)event;

@end

static void KKGLViewElement_event_cb (kk::EventEmitter * emitter,kk::CString name,kk::Event * event,void * context) {
    
    @autoreleasepool{
        
        kk::ElementEvent * e = dynamic_cast<kk::ElementEvent *>(event);
        
        if(e) {
            
            kk::script::Object * data = e->data.as<kk::script::Object>();
            
            if(data && data->heapptr() && data->jsContext() ) {
                
                duk_context * ctx = data->jsContext();
                
                NSString * n = [NSString stringWithCString:name + 4 encoding:NSUTF8StringEncoding];
                
                duk_push_heapptr(ctx, data->heapptr());
                
                id v = [KKGLContext JSContextToObject: -1 ctx:ctx];
                
                duk_pop(ctx);
                
                KKGLViewElement * elem = (__bridge KKGLViewElement *) context;
                
                dispatch_async(dispatch_get_main_queue(), ^{
                    
                    KKElementEvent * ev = [[KKElementEvent alloc] initWithElement:elem];
                    ev.data = v;
                    [elem _emit:n event:ev];
                    
                });
                
            }
        }
    }
    
}

@implementation KKGLViewElement

@synthesize element = _element;

+(void) initialize {
    [super initialize];
    [KKViewContext setDefaultElementClass:[KKGLViewElement class] name:@"gl:view"];
}

-(void) dealloc {
    
    if(_source) {
        dispatch_source_cancel(_source);
        _source = nil;
    }
    
    if(_GLContext) {
        KKGLContext * v = _GLContext;
        dispatch_async(v.queue, ^{
            [v recycle];
        });
        _GLContext = nil;
    }
    
    if(_element != nil) {
        _element->off("app_*", KKGLViewElement_event_cb, nullptr);
        _element->release();
    }
    
}

-(kk::GA::Element *) element {
    if(_element == nil) {
        _element = new kk::GA::Element();
        _element->retain();
        _element->on("app_*", KKGLViewElement_event_cb, (__bridge void *) self);
    }
    return _element;
}

-(instancetype) init {
    if((self = [super init])) {
        [self set:@"view" value:NSStringFromClass([KKGLView class])];
    }
    return self;
}

-(void) openGame {
    
    if(_element == nullptr) {
        
        NSString * basePath = [self.viewContext basePath];
        
        NSString * path = [self get:@"path"];
        
        if(basePath != nil) {
            path = [basePath stringByAppendingPathComponent:path];
        }
        
        if(path) {
            
            __weak KKGLViewElement * element = self;
            
            dispatch_async(self.GLContext.queue, ^{
                
                if(element) {
                    
                    KKGLContext * GLContext = element.GLContext;
                    
                    GLContext.GLContext->setBasePath([path UTF8String]);
                    
                    NSString * code = [NSString stringWithContentsOfFile:[path stringByAppendingPathComponent:@"main.js"] encoding:NSUTF8StringEncoding error:nil];
                    
                    if(code != nil) {
                        
                        NSString * evelCode = [NSString stringWithFormat:@"(function(element) { %@ } )",code];
                        
                        duk_context * ctx = GLContext.JSContext->jsContext();
                        
                        duk_eval_string(ctx, [evelCode UTF8String]);
                        
                        if(duk_is_function(ctx, -1)) {
                            
                            kk::script::PushObject(ctx, element.element);
                            
                            if(DUK_EXEC_SUCCESS != duk_pcall(ctx, 1)) {
                                kk::script::Error(ctx, -1);
                            }
                            
                            duk_pop(ctx);
                            
                        } else {
                            duk_pop(ctx);
                        }
                    }
                    
                }
            });
            
        }
        
        if(_source == nil) {
            
            _source = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0,self.GLContext.queue);
            
            int64_t frames = [[self get:@"speed"] intValue];
            
            if(frames <=0){
                frames = 30;
            }
            
            self.GLContext.GLContext->setFrames((kk::Uint) frames);
            
            dispatch_source_set_timer(_source, dispatch_walltime(NULL, 0), (int64_t)(NSEC_PER_SEC / frames), 0);
            
            __weak KKGLViewElement * element = self;
            
            dispatch_source_set_event_handler(_source, ^{
                [element tick];
            });
            
            dispatch_resume(_source);
        }
        
    }
}

-(void) setView:(UIView *)view {
    
    [EAGLContext setCurrentContext:_GLContext];
    
    [(KKGLView *) view setOnVisible:nil];
    
    if(_source) {
        dispatch_source_cancel(_source);
        _source = nil;
    }
    
    if(_element != nil) {
        _element->release();
        _element = nullptr;
    }
    
    if(_GLContext) {
        KKGLContext * v = _GLContext;
        dispatch_async(v.queue, ^{
            [v recycle];
        });
        _GLContext = nil;
    }
    
    [super setView:view];
    
    if(view ) {
        
        if([(KKGLView *) view isVisible]) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self openGame];
            });
        } else {
            __weak KKGLViewElement * element = self;
            [(KKGLView *) view setOnVisible:^(BOOL visible) {
                if(visible) {
                    [element openGame];
                }
            }];
        }

    }
}

-(void) obtainView:(UIView *)view {
    [super obtainView:view];
    
    
}

-(KKGLContext *) GLContext {
    if(_GLContext == nil){
        _GLContext = [[KKGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES2];
    }
    return _GLContext;
}

-(void) changedKey:(NSString *)key{
    [super changedKey:key];
}


-(void) tick {
    
    [(KKGLView *) self.view displayGLContext:self.GLContext element:self.element];
    
    if(!_loaded && self.GLContext.GLContext->loadingProgress(self.element) >= 1.0f) {
        
        _loaded = YES;
        
        __weak KKGLViewElement * element = self;
        
        dispatch_async(dispatch_get_main_queue(), ^{
            
            if(element) {
                KKElementEvent * e = [[KKElementEvent alloc] initWithElement:element];
                e.data = element.data;
                [element emit:@"load" event:e];
            }
            
        });
        
        
    }
}

-(void) _emit:(NSString *)name event:(KKEvent *)event {
    [super emit:name event:event];
}

-(void) emit:(NSString *)name event:(KKEvent *)event {
    [super emit:name event:event];
    
    if([event isKindOfClass:[KKElementEvent class]]
       && _GLContext
       && _element) {
        
        id data = [(KKElementEvent *) event data];
        
        dispatch_async(self.GLContext.queue, ^{
            
            kk::Strong v = new kk::ElementEvent();
            
            kk::ElementEvent * ev = v.as<kk::ElementEvent>();
            
            duk_context * ctx = _GLContext.JSContext->jsContext();
            
            [KKGLContext JSContextPushObject:data ctx:ctx];
            
            ev->data = new kk::script::Object(_GLContext.JSContext,-1);
            
            duk_pop(ctx);
            
            _element->emit([name UTF8String], ev);
            
        });
        
    }
}

@end

@implementation KKGLView (KKViewElement)

-(void) KKViewElementDidLayouted:(KKViewElement *)element {
    [super KKViewElementDidLayouted:element];
    
    [self resizeGLContext:[(KKGLViewElement *) element GLContext]];
}

-(void) KKElementObtainView:(KKViewElement *)element {
    [super KKElementObtainView:element];
    [self installGLContext:[(KKGLViewElement *) element GLContext]];
    [self resizeGLContext:[(KKGLViewElement *) element GLContext]];
}

-(void) KKElementRecycleView:(KKViewElement *)element {
    [super KKElementRecycleView:element];
    [self uninstallGLContext:[(KKGLViewElement *) element GLContext]];
}

@end
