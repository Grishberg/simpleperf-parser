// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.android.tools.profilers.cpu.nodemodel;

import com.android.tools.profilers.cpu.CaptureNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides accessors to the basic attributes of data represented by {@link CaptureNode} (e.g. Java methods, native functions, etc.).
 */
public interface CaptureNodeModel {

    /**
     * @return a tag for the purpose of collapsing into coarser nodes
     */
    @Nullable
    default String getTag() {
        return null;
    }

    @NotNull
    String getName();

    /**
     * @return name concatenated by a suffix. The suffix is empty by default.
     */
    @NotNull
    default String getNameWithSuffix() {
        return getName();
    }

    @NotNull
    String getFullName();

    /**
     * @return full name concatenated by a suffix. The suffix is empty by default.
     */
    @NotNull
    default String getFullNameWithSuffix() {
        return getFullName();
    }

    @NotNull
    String getId();
}
