/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.objects.shared;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import org.truffleruby.language.RubyBaseNode;
import org.truffleruby.language.objects.ShapeCachingGuards;

@ImportStatic(ShapeCachingGuards.class)
public abstract class WriteBarrierNode extends RubyBaseNode {

    protected static final int CACHE_LIMIT = 8;
    protected static final int MAX_DEPTH = 3;

    protected final int depth;

    public static WriteBarrierNode create() {
        return WriteBarrierNodeGen.create(0);
    }

    public WriteBarrierNode(int depth) {
        this.depth = depth;
    }

    public abstract void executeWriteBarrier(Object value);

    @Specialization(
            guards = { "value.getShape() == cachedShape", "depth < MAX_DEPTH" },
            assumptions = "cachedShape.getValidAssumption()",
            limit = "CACHE_LIMIT")
    protected void writeBarrierCached(DynamicObject value,
            @Cached("value.getShape()") Shape cachedShape,
            @Cached("isShared(cachedShape)") boolean alreadyShared,
            @Cached("createShareObjectNode(alreadyShared)") ShareObjectNode shareObjectNode) {
        if (!alreadyShared) {
            shareObjectNode.executeShare(value);
        }
    }

    @Specialization(guards = "updateShape(value)")
    public void updateShapeAndWriteBarrier(DynamicObject value) {
        executeWriteBarrier(value);
    }

    @Specialization(replaces = { "writeBarrierCached", "updateShapeAndWriteBarrier" })
    protected void writeBarrierUncached(DynamicObject value) {
        SharedObjects.writeBarrier(getContext(), value);
    }

    @Specialization(guards = "!isDynamicObject(value)")
    protected void noWriteBarrier(Object value) {
    }

    protected boolean isShared(Shape shape) {
        return SharedObjects.isShared(getContext(), shape);
    }

    protected ShareObjectNode createShareObjectNode(boolean alreadyShared) {
        if (!alreadyShared) {
            return ShareObjectNodeGen.create(depth + 1);
        } else {
            return null;
        }
    }

}
