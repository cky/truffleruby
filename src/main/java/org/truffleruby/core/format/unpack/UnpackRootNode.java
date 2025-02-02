/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.unpack;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import org.truffleruby.RubyContext;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.format.FormatFrameDescriptor;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.language.RubyBaseRootNode;
import org.truffleruby.language.backtrace.InternalRootNode;

public class UnpackRootNode extends RubyBaseRootNode implements InternalRootNode {

    private final RubyContext context;

    @Child private FormatNode child;

    @CompilationFinal private int expectedLength;

    public UnpackRootNode(RubyContext context, SourceSection sourceSection, FormatNode child) {
        super(context.getLanguage(), FormatFrameDescriptor.FRAME_DESCRIPTOR, sourceSection);
        this.context = context;
        this.child = child;
        expectedLength = context.getOptions().ARRAY_UNINITIALIZED_SIZE;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final Object[] arguments = frame.getArguments();

        frame.setObject(FormatFrameDescriptor.SOURCE_SLOT, arguments[0]);
        frame.setInt(FormatFrameDescriptor.SOURCE_LENGTH_SLOT, (int) arguments[1]);
        frame.setInt(FormatFrameDescriptor.SOURCE_POSITION_SLOT, 0);
        frame.setBoolean(FormatFrameDescriptor.SOURCE_TAINTED_SLOT, (boolean) arguments[2]);
        frame.setObject(FormatFrameDescriptor.SOURCE_ASSOCIATED_SLOT, arguments[3]);
        frame.setObject(FormatFrameDescriptor.OUTPUT_SLOT, new Object[expectedLength]);
        frame.setInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT, 0);
        frame.setBoolean(FormatFrameDescriptor.TAINT_SLOT, false);

        child.execute(frame);

        final int outputLength;

        try {
            outputLength = frame.getInt(FormatFrameDescriptor.OUTPUT_POSITION_SLOT);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException(e);
        }

        if (outputLength > expectedLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            expectedLength = ArrayUtils.capacity(context, expectedLength, outputLength);
        }

        final Object[] output;

        try {
            output = (Object[]) frame.getObject(FormatFrameDescriptor.OUTPUT_SLOT);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException(e);
        }

        final boolean taint;

        try {
            taint = frame.getBoolean(FormatFrameDescriptor.TAINT_SLOT);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException(e);
        }

        return new ArrayResult(output, outputLength, taint);
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    public String getName() {
        return "unpack";
    }

    @Override
    public String toString() {
        return getName();
    }

}
