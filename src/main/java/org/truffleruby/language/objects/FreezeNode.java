/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

package org.truffleruby.language.objects;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.Layouts;
import org.truffleruby.language.RubyBaseNode;

public abstract class FreezeNode extends RubyBaseNode {

    public static FreezeNode create() {
        return FreezeNodeGen.create();
    }

    public abstract Object executeFreeze(Object object);

    @Specialization
    public Object freeze(boolean object) {
        return object;
    }

    @Specialization
    public Object freeze(int object) {
        return object;
    }

    @Specialization
    public Object freeze(long object) {
        return object;
    }

    @Specialization
    public Object freeze(double object) {
        return object;
    }

    @Specialization(guards = "isNil(nil)")
    public Object freeze(Object nil) {
        return nil();
    }

    @Specialization(guards = "isRubyBignum(object)")
    public Object freezeBignum(DynamicObject object) {
        return object;
    }

    @Specialization(guards = "isRubySymbol(symbol)")
    public Object freezeSymbol(DynamicObject symbol) {
        return symbol;
    }

    @Specialization(guards = { "!isNil(object)", "!isRubyBignum(object)", "!isRubySymbol(object)" })
    public Object freeze(
            DynamicObject object,
            @Cached WriteObjectFieldNode writeFrozenNode) {
        writeFrozenNode.write(object, Layouts.FROZEN_IDENTIFIER, true);
        return object;
    }
}
