//
//  GABody.h
//  KKGame
//
//  Created by zhanghailong on 2018/2/1.
//  Copyright © 2018年 kkmofang.cn. All rights reserved.
//

#ifndef GABody_h
#define GABody_h

#include "GAContext.h"
#include "GACollision.h"

struct cpBody;

namespace kk {
    
    namespace GA {
       
        
        class Scene;
        class Shape;
        
        enum BodyType {
            BodyTypeFixed,      //固定地
            BodyTypeMovable,    //活动的
        };
        
        class Body : public Collision ,public IPosition {
        public:
            virtual ~Body();
            virtual Point position();
            virtual void setPosition(Point value);
            virtual Float zIndex();
            virtual void changedKey(String& key);
            virtual Scene * scene();
            virtual ::cpBody * cpBody();
            virtual void onWillRemoveFromParent(kk::Element * element);
            
            virtual void exec(Context * context);
            
            virtual kk::Boolean inCollisionShape(Shape * a, Shape * b,Point n);
            virtual void outCollisionShape(Shape * a, Shape * b,Point n);
            
            virtual kk::Boolean inCollisionShape(Shape * shape,Point n);
            virtual void outCollisionShape(Shape * shape,Point n);

            BodyType bodyType;
            Float angle;    //方向弧度
            
            virtual duk_ret_t duk_position(duk_context * ctx);
            virtual duk_ret_t duk_setPosition(duk_context * ctx);
            
            DEF_SCRIPT_CLASS_NOALLOC
            
            KK_DEF_ELEMENT_CREATE(Body)
        
        protected:
            Point _position;
            Float _zIndex;
            ::cpBody * _cpBody;
        };

    }
    
}

#endif /* GABody_h */
