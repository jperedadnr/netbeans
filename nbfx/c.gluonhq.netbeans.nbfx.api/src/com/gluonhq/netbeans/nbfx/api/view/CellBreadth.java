/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.api.view;

/**
 * The widest preferred width the cells of a tree have reported since the rows were last replaced,
 * which every cell then reports as its own.
 * <p>
 * A virtual flow decides whether it needs a horizontal scroll bar from the cells on screen only.
 * When the widest row sits at the bottom edge and barely overflows, the bar appears, shrinks the
 * viewport, pushes that row out of view, and no longer sees a reason to be shown: it hides, the row
 * comes back, and the layout oscillates for ever. With every cell as wide as the widest one seen,
 * the decision no longer depends on which rows are visible. The width only grows while the rows
 * stay, so the layout settles after the widest row has been measured once.
 * <p>
 * A tree (or list) keeps one instance, {@link #reset() resets} it whenever it replaces its rows, and
 * its cells override {@code computePrefWidth} to return {@link #widen widen(own width)} for a
 * non-empty cell. FX thread.
 */
public final class CellBreadth {

    private double max;

    /** Forgets the width: the rows are being replaced. */
    public void reset() {
        max = 0;
    }

    /** Records {@code width}, the preferred width a cell computed for itself, and returns the width it reports. */
    public double widen(double width) {
        if (width > max) {
            max = width;
        }
        return max;
    }
}
