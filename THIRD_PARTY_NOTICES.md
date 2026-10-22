# Third-party notices

This app is built on open-source components. This file lists every dependency,
the license it was verified under, and where that license was read. The machine
readable inventory is `third_party.json`; `scripts/check_third_party.py` (run by
`scripts/verify.sh`) fails when the two or `gradle/libs.versions.toml` disagree.
The app also ships this file as an asset and shows it under Settings.

Components are linked as libraries unless a row says a file was copied. Nothing
here is modified. No component adds a network path, telemetry or cloud backup.
This repository has no license file of its own (all rights reserved); nothing
below relicenses an upstream component.

Components with scope `test` or `build` are used to build and verify the app and
are not part of the shipped APK; they are listed for completeness.


## Shipped in the app

### AndroidX Core KTX

- Version: 1.15.0
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.15.0/core-ktx-1.15.0.pom
- Use: Linked library (not copied)

### AndroidX AppCompat

- Version: 1.7.0
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/appcompat/appcompat/1.7.0/appcompat-1.7.0.pom
- Use: Linked library (not copied)

### AndroidX ConstraintLayout

- Version: 2.1.4
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/constraintlayout/constraintlayout/2.1.4/constraintlayout-2.1.4.pom
- Use: Linked library (not copied)

### AndroidX Fragment

- Version: 1.8.5
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/fragment/fragment-ktx/1.8.5/fragment-ktx-1.8.5.pom
- Use: Linked library (not copied)

### AndroidX Activity

- Version: 1.9.3
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/activity/activity-ktx/1.9.3/activity-ktx-1.9.3.pom
- Use: Linked library (not copied)

### AndroidX RecyclerView

- Version: 1.4.0
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/recyclerview/recyclerview/1.4.0/recyclerview-1.4.0.pom
- Use: Linked library (not copied)

### AndroidX Lifecycle

- Version: 2.8.7
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-ktx/2.8.7/lifecycle-viewmodel-ktx-2.8.7.pom
- Use: Linked library (not copied)

### AndroidX SQLite

- Version: 2.6.2
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite/2.6.2/sqlite-2.6.2.pom
- Use: Linked library (not copied)

### AndroidX Biometric

- Version: 1.1.0
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/biometric/biometric/1.1.0/biometric-1.1.0.pom
- Use: Linked library (not copied)

### AndroidX Room runtime

- Version: 2.7.0
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.7.0/room-runtime-2.7.0.pom
- Use: Linked library (not copied)

### Material Components for Android

- Version: 1.12.0
- License: Apache-2.0
- Upstream: https://github.com/material-components/material-components-android
- License read from: https://raw.githubusercontent.com/material-components/material-components-android/1.12.0/LICENSE
- Use: Linked library (not copied)

### kotlinx.coroutines (Android)

- Version: 1.8.1
- License: Apache-2.0
- Upstream: https://github.com/Kotlin/kotlinx.coroutines
- License read from: https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.8.1/LICENSE.txt
- Use: Linked library (not copied)

### kotlinx.serialization (JSON)

- Version: 1.7.3
- License: Apache-2.0
- Upstream: https://github.com/Kotlin/kotlinx.serialization
- License read from: https://raw.githubusercontent.com/Kotlin/kotlinx.serialization/v1.7.3/LICENSE.txt
- Use: Linked library (not copied); also brings kotlinx-serialization-core

### Calendar (Kizito Nwose)

- Version: 2.6.2
- License: MIT
- Upstream: https://github.com/kizitonwose/Calendar
- License read from: https://raw.githubusercontent.com/kizitonwose/Calendar/2.6.2/LICENSE.md
- Use: Linked library (not copied): month view; also brings calendar core and data modules

### SQLCipher for Android (Zetetic)

- Version: 4.17.0
- License: BSD-3-Clause
- Upstream: https://github.com/sqlcipher/sqlcipher-android
- License read from: https://raw.githubusercontent.com/sqlcipher/sqlcipher-android/v4.17.0/LICENSE
- Use: Linked library (not copied): libsqlcipher.so for arm64-v8a, armeabi-v7a, x86, x86_64, with SQLCipher core, SQLite and LibTomCrypt compiled in
- Note: License type: Zetetic's BSD-style licence (three conditions plus disclaimer, the BSD-3-Clause text with Zetetic as the named party); the Maven POM names only https://www.zetetic.net/sqlcipher/license/. SQLCipher core licence read from https://raw.githubusercontent.com/sqlcipher/sqlcipher/v4.17.0/LICENSE.md (the release tag matching this version; the Android repository's submodule pointer was not inspected). Bundled LibTomCrypt (the default crypto provider) is public domain or WTFPL, read from https://raw.githubusercontent.com/sqlcipher/libtomcrypt/master/LICENSE; SQLite is public domain.


## Test only (not shipped)

### AndroidX Room testing

- Version: 2.7.0
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/room/room-testing/2.7.0/room-testing-2.7.0.pom
- Use: Linked library (not copied)

### kotlinx.coroutines test

- Version: 1.8.1
- License: Apache-2.0
- Upstream: https://github.com/Kotlin/kotlinx.coroutines
- License read from: https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.8.1/LICENSE.txt
- Use: Test dependency, not shipped

### JUnit 4

- Version: 4.13.2
- License: EPL-1.0
- Upstream: https://github.com/junit-team/junit4
- License read from: https://raw.githubusercontent.com/junit-team/junit4/r4.13.2/LICENSE-junit.txt
- Use: Test dependency, not shipped

### Robolectric

- Version: 4.14.1
- License: MIT
- Upstream: https://github.com/robolectric/robolectric
- License read from: https://raw.githubusercontent.com/robolectric/robolectric/robolectric-4.14.1/LICENSE
- Use: Test dependency, not shipped

### AndroidX Test core

- Version: 1.6.1
- License: Apache-2.0
- Upstream: https://github.com/android/android-test
- License read from: https://dl.google.com/dl/android/maven2/androidx/test/core/1.6.1/core-1.6.1.pom
- Use: Test dependency, not shipped

### AndroidX Test ext-junit

- Version: 1.2.1
- License: Apache-2.0
- Upstream: https://github.com/android/android-test
- License read from: https://dl.google.com/dl/android/maven2/androidx/test/ext/junit/1.2.1/junit-1.2.1.pom
- Use: Test dependency, not shipped

### AndroidX Test Espresso core

- Version: 3.5.1
- License: Apache-2.0
- Upstream: https://github.com/android/android-test
- License read from: https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-core/3.5.1/espresso-core-3.5.1.pom
- Use: Test dependency, not shipped

### Roborazzi

- Version: 1.45.1
- License: Apache-2.0
- Upstream: https://github.com/takahirom/roborazzi
- License read from: https://raw.githubusercontent.com/takahirom/roborazzi/1.45.1/LICENSE
- Use: Test dependency, not shipped

### Accessibility Test Framework for Android

- Version: 4.1.1
- License: Apache-2.0
- Upstream: https://github.com/google/Accessibility-Test-Framework-for-Android
- License read from: https://dl.google.com/dl/android/maven2/com/google/android/apps/common/testing/accessibility/framework/accessibility-test-framework/4.1.1/accessibility-test-framework-4.1.1.pom
- Use: Transitive test dependency of roborazzi-accessibility-check, not shipped
- Note: Also checked against https://raw.githubusercontent.com/google/Accessibility-Test-Framework-for-Android/master/LICENSE (no tag carries a LICENSE file).


## Build tooling (not shipped)

### AndroidX Room compiler (KSP processor)

- Version: 2.7.0
- License: Apache-2.0
- Upstream: https://developer.android.com/jetpack/androidx
- License read from: https://dl.google.com/dl/android/maven2/androidx/room/room-compiler/2.7.0/room-compiler-2.7.0.pom
- Use: Linked library (not copied)

### Roborazzi Gradle plugin

- Version: 1.45.1
- License: Apache-2.0
- Upstream: https://github.com/takahirom/roborazzi
- License read from: https://raw.githubusercontent.com/takahirom/roborazzi/1.45.1/LICENSE
- Use: Build plugin, not shipped

### Android Gradle Plugin

- Version: 8.7.2
- License: Apache-2.0
- Upstream: https://developer.android.com/build
- License read from: https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/8.7.2/gradle-8.7.2.pom
- Use: Build plugin, not shipped

### Kotlin Gradle plugin

- Version: 2.0.21
- License: Apache-2.0
- Upstream: https://github.com/JetBrains/kotlin
- License read from: https://raw.githubusercontent.com/JetBrains/kotlin/v2.0.21/license/LICENSE.txt
- Use: Build plugin, not shipped

### Kotlin serialization compiler plugin

- Version: 2.0.21
- License: Apache-2.0
- Upstream: https://github.com/JetBrains/kotlin
- License read from: https://raw.githubusercontent.com/JetBrains/kotlin/v2.0.21/license/LICENSE.txt
- Use: Build plugin, not shipped

### Kotlin Symbol Processing (KSP)

- Version: 2.0.21-1.0.28
- License: Apache-2.0
- Upstream: https://github.com/google/ksp
- License read from: https://raw.githubusercontent.com/google/ksp/2.0.21-1.0.28/LICENSE
- Use: Build plugin, not shipped

### Gradle Wrapper

- Version: 8.9
- License: Apache-2.0
- Upstream: https://github.com/gradle/gradle
- License read from: https://raw.githubusercontent.com/gradle/gradle/v8.9.0/LICENSE
- Use: gradle/wrapper/gradle-wrapper.jar; gradlew; gradlew.bat
- Note: Wrapper files are the unmodified Gradle 8.9 distribution wrapper; they run the build and are not shipped in the APK.


## Assets and fonts

No third-party font, icon set, image or sound was added. The app uses the system
font and the Material Components theme. Icons that predate the open-source
inventory (launcher and navigation vectors) were authored for this app; their
provenance was not re-audited here.


## License texts

### Apache License 2.0

Applies to every component above whose license is Apache-2.0.
The text below is the canonical Apache-2.0 text as read from the Roborazzi
repository at tag 1.45.1; the individual copyright holders are the upstream
projects named above.

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] [name of copyright owner]

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```


### MIT License (Calendar by Kizito Nwose)

Applies to the Calendar library (`com.kizitonwose.calendar`), shipped in the app.
Read from https://raw.githubusercontent.com/kizitonwose/Calendar/2.6.2/LICENSE.md

```
The MIT License (MIT)

Copyright (c) 2019 Kizito Nwose

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### SQLCipher for Android (Zetetic LLC)

Applies to `net.zetetic:sqlcipher-android` and the SQLCipher core compiled into its
native library (`libsqlcipher.so`), shipped in the app to encrypt the database.
Read from https://raw.githubusercontent.com/sqlcipher/sqlcipher-android/v4.17.0/LICENSE
and https://raw.githubusercontent.com/sqlcipher/sqlcipher/v4.17.0/LICENSE.md

```
Copyright (c) 2008-2023, ZETETIC LLC
Copyright (c) 2025, ZETETIC LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the ZETETIC LLC nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

The same native library also contains SQLite (public domain) and LibTomCrypt, the
crypto provider SQLCipher for Android uses by default (public domain, or the WTFPL,
at the user's choice; read from
https://raw.githubusercontent.com/sqlcipher/libtomcrypt/master/LICENSE).

