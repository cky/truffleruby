/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.stdlib.bigdecimal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.truffleruby.Layouts;

import java.math.BigDecimal;
import java.math.MathContext;

public abstract class AbstractDivNode extends BigDecimalOpNode {

    private final ConditionProfile normalZero = ConditionProfile.createBinaryProfile();

    private Object divBigDecimalConsideringSignum(DynamicObject a, DynamicObject b, MathContext mathContext) {
        final BigDecimal aBigDecimal = Layouts.BIG_DECIMAL.getValue(a);
        final BigDecimal bBigDecimal = Layouts.BIG_DECIMAL.getValue(b);

        if (normalZero.profile(bBigDecimal.signum() == 0)) {
            switch (aBigDecimal.signum()) {
                case 1:
                    return BigDecimalType.POSITIVE_INFINITY;
                case 0:
                    return BigDecimalType.NAN;
                case -1:
                    return BigDecimalType.NEGATIVE_INFINITY;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException("unreachable code branch for value: " + aBigDecimal.signum());
            }
        } else {
            return divBigDecimal(aBigDecimal, bBigDecimal, mathContext);
        }
    }

    @TruffleBoundary
    private BigDecimal divBigDecimal(BigDecimal a, BigDecimal b, MathContext mathContext) {
        return a.divide(b, mathContext);
    }

    protected Object div(DynamicObject a, DynamicObject b, int precision) {
        if (precision == 0) {
            precision = getLimit();
        }
        return createBigDecimal(divBigDecimalConsideringSignum(a, b, new MathContext(precision, getRoundMode())));
    }

    protected Object divNormalSpecial(DynamicObject a, DynamicObject b, int precision) {
        Object value = null;

        switch (Layouts.BIG_DECIMAL.getType(b)) {
            case NAN:
                value = BigDecimalType.NAN;
                break;
            case NEGATIVE_ZERO:
                switch (Layouts.BIG_DECIMAL.getValue(a).signum()) {
                    case 1:
                        value = BigDecimalType.NEGATIVE_INFINITY;
                        break;
                    case 0:
                        value = BigDecimalType.NAN;
                        break;
                    case -1:
                        value = BigDecimalType.POSITIVE_INFINITY;
                        break;
                }
                break;
            case POSITIVE_INFINITY:
                switch (Layouts.BIG_DECIMAL.getValue(a).signum()) {
                    case 1:
                    case 0:
                        value = BigDecimal.ZERO;
                        break;
                    case -1:
                        value = BigDecimalType.NEGATIVE_ZERO;
                        break;
                }
                break;
            case NEGATIVE_INFINITY:
                switch (Layouts.BIG_DECIMAL.getValue(b).signum()) {
                    case 1:
                        value = BigDecimalType.NEGATIVE_ZERO;
                        break;
                    case 0:
                    case -1:
                        value = BigDecimal.ZERO;
                        break;
                }
                break;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException("unreachable code branch for value: " + Layouts.BIG_DECIMAL.getType(b));
        }

        return createBigDecimal(value);
    }

    protected Object divSpecialNormal(DynamicObject a, DynamicObject b, int precision) {
        Object value = null;

        switch (Layouts.BIG_DECIMAL.getType(a)) {
            case NAN:
                value = BigDecimalType.NAN;
                break;
            case NEGATIVE_ZERO:
                switch (Layouts.BIG_DECIMAL.getValue(b).signum()) {
                    case 1:
                        value = BigDecimalType.NEGATIVE_ZERO;
                        break;
                    case 0:
                        value = BigDecimalType.NAN;
                        break;
                    case -1:
                        value = BigDecimal.ZERO;
                        break;
                }
                break;
            case POSITIVE_INFINITY:
                switch (Layouts.BIG_DECIMAL.getValue(b).signum()) {
                    case 1:
                    case 0:
                        value = BigDecimalType.POSITIVE_INFINITY;
                        break;
                    case -1:
                        value = BigDecimalType.NEGATIVE_INFINITY;
                        break;
                }
                break;
            case NEGATIVE_INFINITY:
                switch (Layouts.BIG_DECIMAL.getValue(b).signum()) {
                    case 1:
                    case 0:
                        value = BigDecimalType.NEGATIVE_INFINITY;
                        break;
                    case -1:
                        value = BigDecimalType.POSITIVE_INFINITY;
                        break;
                }
                break;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException("unreachable code branch for value: " + Layouts.BIG_DECIMAL.getType(a));
        }

        return createBigDecimal(value);
    }

    protected Object divSpecialSpecial(DynamicObject a, DynamicObject b, int precision) {
        final BigDecimalType aType = Layouts.BIG_DECIMAL.getType(a);
        final BigDecimalType bType = Layouts.BIG_DECIMAL.getType(b);

        if (aType == BigDecimalType.NAN || bType == BigDecimalType.NAN ||
                (aType == BigDecimalType.NEGATIVE_ZERO && bType == BigDecimalType.NEGATIVE_ZERO)) {
            return createBigDecimal(BigDecimalType.NAN);
        }

        if (aType == BigDecimalType.NEGATIVE_ZERO) {
            if (bType == BigDecimalType.POSITIVE_INFINITY) {
                return createBigDecimal(BigDecimalType.NEGATIVE_ZERO);
            } else {
                return createBigDecimal(BigDecimalType.POSITIVE_INFINITY);
            }
        }

        if (bType == BigDecimalType.NEGATIVE_ZERO) {
            if (aType == BigDecimalType.POSITIVE_INFINITY) {
                return createBigDecimal(BigDecimalType.NEGATIVE_INFINITY);
            } else {
                return createBigDecimal(BigDecimalType.POSITIVE_INFINITY);
            }
        }

        // a and b are only +-Infinity
        return createBigDecimal(BigDecimalType.NAN);
    }
}
