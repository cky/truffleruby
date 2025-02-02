/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.write.array;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.truffleruby.core.array.ArrayUtils;
import org.truffleruby.core.format.FormatNode;
import org.truffleruby.core.format.MissingValue;
import org.truffleruby.core.format.write.OutputNode;

@NodeChild(value = "output", type = OutputNode.class)
@NodeChild(value = "value", type = FormatNode.class)
public abstract class WriteValueNode extends FormatNode {

    @Specialization
    public Object doWrite(Object output, MissingValue value) {
        return null;
    }

    @Specialization(guards = "!isMissingValue(value)")
    public Object doWrite(VirtualFrame frame, Object[] output, Object value) {
        final Object[] outputWithEnoughSize = ensureCapacity(frame, output, 1);
        final int outputPosition = getOutputPosition(frame);
        outputWithEnoughSize[outputPosition] = value;
        setOutputPosition(frame, outputPosition + 1);
        return null;
    }

    private Object[] ensureCapacity(VirtualFrame frame, Object[] output, int length) {
        final int outputPosition = getOutputPosition(frame);
        final int neededLength = outputPosition + length;

        if (neededLength <= output.length) {
            return output;
        }

        // If we ran out of output byte[], deoptimize and next time we'll allocate more
        CompilerDirectives.transferToInterpreterAndInvalidate();

        final Object[] newOutput = new Object[ArrayUtils.capacity(getContext(), output.length, neededLength)];
        System.arraycopy(output, 0, newOutput, 0, outputPosition);
        setOutput(frame, newOutput);

        return newOutput;
    }

}
