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

/**
 * Simple model that represents a syscall (e.g. write or ioctl).
 */
public class SyscallModel extends NativeNodeModel {

    private final String myTag;

    public SyscallModel(String name) {
        this("", name);
    }

    /**
     * @param tag a tag as a coarser specification of this node, used for collapsing
     * @param name the name to display this symbol by
     */
    public SyscallModel(String tag, String name) {
        myTag = tag;
        myName = name;
    }

    @Override
    public String getTag() {
        return myTag;
    }
}
