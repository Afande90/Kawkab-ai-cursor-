# Licence audit

Date: 28 Oct 2026 (Asia/Dubai).

Scope: every library resolved on `fossReleaseRuntimeClasspath` (391 artifacts after version conflict resolution), plus bundled assets, the `full`-only Crashlytics SDK, models, voices, datasets, and material the spec names as reference-only.

M1, 29 Oct 2026: no new third-party library. OpenDroid is adapted source and is recorded in the OpenDroid row of this audit. The stub messenger is first-party test code and is not in the foss APK.

How a library row was verified: the licence element of that artifact's Maven POM in the Gradle module cache, unless the POM has none. Those exceptions cite the upstream licence URL or the jar's `META-INF/LICENSE.md` and are listed in `DECISIONS.md`.

`shipped in APK` is `yes` when the artifact is on the foss release runtime classpath or bundled as an asset. A version that appears in the tree only as a requested version upgraded by Gradle (`->`) is recorded at the resolved version, once.

The image loader (`io.coil-kt.coil3:coil-compose:3.6.3`) is a direct dependency and is credited in `NOTICE`. The About screen does not name it: `AboutAcknowledgmentsTest` still treats that name as a stale credit. See `DECISIONS.md`.

## Libraries on the foss release runtime classpath

| Component | Licence | Terms | Verified from | Shipped in APK |
|---|---|---|---|---|
| `ai.koog:agents-core:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-core-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-event-handler:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-event-handler-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-memory:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-memory-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-snapshot:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-snapshot-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-tokenizer:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-tokenizer-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-trace:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-features-trace-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-mcp:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-mcp-android:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-mcp-metadata:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-mcp-metadata-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-tools:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-tools-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-utils:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:agents-utils-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:embeddings-base:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:embeddings-base-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:embeddings-llm:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:embeddings-llm-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:http-client-core:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:http-client-core-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:http-client-ktor:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:http-client-ktor-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:koog-agents:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:koog-agents-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-cache-files:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-cache-files-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-cache-model:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-cache-model-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-bedrock-client:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-bedrock-client-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-cached:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-cached-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-clients:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-clients-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-deepseek-client:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-deepseek-client-android:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-google-client:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-google-client-android:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-llms-all:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-llms-all-android:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-mistralai-client:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-mistralai-client-android:1.3.0-beta` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-model:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-model-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-ollama-client:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-ollama-client-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-openai-client:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-openai-client-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-openai-client-base:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-openai-client-base-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-openrouter-client:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-executor-openrouter-client-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-llm:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-llm-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-markdown:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-markdown-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-model:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-model-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-processor:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-processor-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-structure:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-structure-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-tokenizer:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-tokenizer-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-xml:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:prompt-xml-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:rag-base:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:rag-base-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:serialization-core:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:serialization-core-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:serialization-jackson:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:utils:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `ai.koog:utils-android:1.3.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.activity:activity:1.13.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.activity:activity-compose:1.13.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.activity:activity-ktx:1.13.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.annotation:annotation:1.10.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.annotation:annotation-experimental:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.annotation:annotation-jvm:1.10.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.appcompat:appcompat-resources:1.7.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.appfunctions:appfunctions:1.0.0-alpha12` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.appsearch:appsearch:1.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.appsearch:appsearch-platform-storage:1.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.autofill:autofill:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.collection:collection:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.collection:collection-jvm:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.collection:collection-ktx:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose:compose-bom:2026.09.00` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.animation:animation:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.animation:animation-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.animation:animation-core:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.animation:animation-core-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.foundation:foundation:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.foundation:foundation-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.foundation:foundation-layout:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.foundation:foundation-layout-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.material:material-icons-core:1.7.8` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.material:material-icons-core-android:1.7.8` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.material:material-icons-extended-android:1.7.8` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.material:material-ripple:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.material:material-ripple-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.material3:material3-android:1.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime-annotation:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime-annotation-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime-retain:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime-retain-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime-saveable:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.runtime:runtime-saveable-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-geometry:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-geometry-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-graphics:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-graphics-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-text:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-text-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-tooling-preview-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-unit:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-unit-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-util:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.compose.ui:ui-util-android:1.12.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.concurrent:concurrent-futures-ktx:1.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.core:core:1.19.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.core:core-ktx:1.19.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.core:core-splashscreen:1.2.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.core:core-viewtree:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.customview:customview:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-android:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-core:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-core-android:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-core-okio:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-core-okio-jvm:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-preferences:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-preferences-android:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-preferences-core:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-preferences-core-android:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.datastore:datastore-preferences-external-protobuf:1.2.1` | BSD-3-Clause | commercial, attribution | https://opensource.org/licenses/BSD-3-Clause | yes |
| `androidx.datastore:datastore-preferences-proto:1.2.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.emoji2:emoji2:1.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.exifinterface:exifinterface:1.4.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.fragment:fragment:1.5.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.hilt:hilt-common:1.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.hilt:hilt-lifecycle-viewmodel:1.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.hilt:hilt-navigation-compose:1.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.hilt:hilt-work:1.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-common:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-common-java8:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-common-jvm:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-livedata:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-livedata-core:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-livedata-core-ktx:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-process:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-runtime:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-runtime-android:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-runtime-compose:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-service:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-viewmodel:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.11.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.loader:loader:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigation:navigation-common:2.10.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigation:navigation-common-android:2.10.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigation:navigation-compose:2.10.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigation:navigation-compose-android:2.10.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigation:navigation-runtime:2.10.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigation:navigation-runtime-android:2.10.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigationevent:navigationevent:1.1.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigationevent:navigationevent-android:1.1.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigationevent:navigationevent-compose:1.1.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.navigationevent:navigationevent-compose-android:1.1.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.profileinstaller:profileinstaller:1.4.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.room:room-common:2.8.5` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.room:room-common-jvm:2.8.5` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.room:room-ktx:2.8.5` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.room:room-runtime:2.8.5` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.room:room-runtime-android:2.8.5` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.savedstate:savedstate:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.savedstate:savedstate-android:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.savedstate:savedstate-compose:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.savedstate:savedstate-compose-android:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.savedstate:savedstate-ktx:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.sqlite:sqlite:2.7.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.sqlite:sqlite-android:2.7.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.sqlite:sqlite-framework:2.7.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.sqlite:sqlite-framework-android:2.7.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.startup:startup-runtime:1.2.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.tracing:tracing-ktx:1.2.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.vectordrawable:vectordrawable:1.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.viewpager:viewpager:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.window:window:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.window:window-core:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.window:window-core-android:1.5.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.work:work-runtime:2.12.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.work:work-runtime-ktx:2.12.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `androidx.work:work-testing:2.12.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.fasterxml.jackson:jackson-bom:2.21.3` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.fasterxml.jackson.core:jackson-annotations:2.21` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.fasterxml.jackson.core:jackson-core:2.21.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.fasterxml.jackson.core:jackson-databind:2.21.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.fasterxml.jackson.module:jackson-module-kotlin:2.21.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.accompanist:accompanist-drawablepainter:0.37.3` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.ai.edge.litertlm:litertlm-android:0.17.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.android.datatransport:transport-api:3.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.android.datatransport:transport-backend-cct:3.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.android.datatransport:transport-runtime:3.1.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.auto.service:auto-service-annotations:1.0-rc6` | Apache-2.0 | commercial, attribution | https://github.com/google/auto/blob/main/LICENSE | yes |
| `com.google.code.findbugs:jsr305:3.0.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.code.gson:gson:2.14.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.dagger:dagger:2.60.1` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.dagger:dagger-lint-aar:2.60.1` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.dagger:hilt-android:2.60.1` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.dagger:hilt-core:2.60.1` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.errorprone:error_prone_annotations:2.48.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.firebase:firebase-encoders:17.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.firebase:firebase-encoders-json:18.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.firebase:firebase-encoders-proto:16.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.flogger:flogger:0.6` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.flogger:flogger-system-backend:0.6` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.guava:failureaccess:1.0.1` | Apache-2.0 | commercial, attribution | https://github.com/google/guava/blob/master/LICENSE | yes |
| `com.google.guava:guava:32.0.1-android` | Apache-2.0 | commercial, attribution | https://github.com/google/guava/blob/master/LICENSE | yes |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | Apache-2.0 | commercial, attribution | https://github.com/google/guava/blob/master/LICENSE | yes |
| `com.google.j2objc:j2objc-annotations:2.8` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.mediapipe:tasks-core:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.mediapipe:tasks-text:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.google.protobuf:protobuf-javalite:4.27.5` | BSD-3-Clause | commercial, attribution | https://github.com/protocolbuffers/protobuf/blob/main/LICENSE | yes |
| `com.jakewharton:process-phoenix:3.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.jakewharton.timber:timber:5.0.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.mikepenz:multiplatform-markdown-renderer:0.45.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.mikepenz:multiplatform-markdown-renderer-android:0.45.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.mikepenz:multiplatform-markdown-renderer-m3:0.45.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.mikepenz:multiplatform-markdown-renderer-m3-android:0.45.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.squareup.okhttp3:okhttp:5.5.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.squareup.okhttp3:okhttp-android:5.5.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.squareup.okio:okio:3.18.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.squareup.okio:okio-jvm:3.18.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `com.typesafe:config:1.4.5` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0 | yes |
| `io.coil-kt.coil3:coil:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.coil-kt.coil3:coil-android:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.coil-kt.coil3:coil-compose:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.coil-kt.coil3:coil-compose-android:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.coil-kt.coil3:coil-compose-core:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.coil-kt.coil3:coil-compose-core-android:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.coil-kt.coil3:coil-core:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.coil-kt.coil3:coil-core-android:3.6.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.github.oshai:kotlin-logging:8.0.01` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.github.oshai:kotlin-logging-android:8.0.01` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-content-negotiation:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-content-negotiation-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-core:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-core-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-logging:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-logging-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-okhttp:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-client-okhttp-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-events:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-events-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-http:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-http-cio:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-http-cio-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-http-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-io:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-io-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-network:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-network-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-serialization:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-serialization-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-serialization-kotlinx:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-serialization-kotlinx-json:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-serialization-kotlinx-json-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-serialization-kotlinx-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-cio:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-cio-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-core:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-core-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-sse:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-sse-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-websockets:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-server-websockets-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-sse:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-sse-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-utils:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-utils-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-websocket-serialization:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-websocket-serialization-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-websockets:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.ktor:ktor-websockets-jvm:3.3.3` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `io.modelcontextprotocol:kotlin-sdk-client:0.11.1` | MIT | commercial, attribution | https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE | yes |
| `io.modelcontextprotocol:kotlin-sdk-client-jvm:0.11.1` | MIT | commercial, attribution | https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE | yes |
| `io.modelcontextprotocol:kotlin-sdk-core:0.11.1` | MIT | commercial, attribution | https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE | yes |
| `io.modelcontextprotocol:kotlin-sdk-core-jvm:0.11.1` | MIT | commercial, attribution | https://github.com/modelcontextprotocol/kotlin-sdk/blob/main/LICENSE | yes |
| `jakarta.inject:jakarta.inject-api:2.0.1` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `javax.inject:javax.inject:1` | Apache-2.0 | commercial, attribution | https://github.com/javax-inject/javax-inject/blob/master/LICENSE.txt | yes |
| `net.zetetic:sqlcipher-android:4.19.0` | BSD-3-Clause | commercial, attribution | https://www.zetetic.net/sqlcipher/license/ and NOTICE | yes |
| `org.bouncycastle:bcprov-jdk18on:1.86` | MIT | commercial, attribution | jar META-INF/LICENSE.md | yes |
| `org.checkerframework:checker-compat-qual:2.5.3` | MIT (dual with GPL-2.0-classpath) | commercial, attribution; Kawkab elects the MIT option | POM dual licence | yes |
| `org.checkerframework:checker-qual:3.33.0` | MIT | commercial, attribution | http://opensource.org/licenses/MIT | yes |
| `org.fusesource.jansi:jansi:2.4.2` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains:annotations:26.0.2-1` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains:markdown:0.7.9` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains:markdown-jvm:0.7.9` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.androidx.lifecycle:lifecycle-common:2.9.6` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.9.6` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.androidx.savedstate:savedstate:1.3.6` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.androidx.savedstate:savedstate-compose:1.3.6` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.animation:animation:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.animation:animation-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.animation:animation-core:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.animation:animation-core-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.foundation:foundation:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.foundation:foundation-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.foundation:foundation-layout:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.foundation:foundation-layout-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.runtime:runtime:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.runtime:runtime-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.runtime:runtime-saveable:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.runtime:runtime-saveable-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-geometry:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-geometry-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-graphics:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-graphics-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-text:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-text-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-unit:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-unit-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-util:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.compose.ui:ui-util-android:1.12.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlin:kotlin-bom:2.3.10` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlin:kotlin-reflect:2.4.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlin:kotlin-stdlib:2.4.20` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlin:kotlin-stdlib-common:2.4.20` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.1` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.5.1` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring:0.9.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-io-core:0.9.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-annotations:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-annotations-jvm:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-generator-core:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-generator-core-jvm:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-generator-json:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-generator-json-jvm:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-json:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-schema-json-jvm:0.4.4` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-io-jvm:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0` | Apache-2.0 | commercial, attribution | https://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 | commercial, attribution | http://www.apache.org/licenses/LICENSE-2.0.txt | yes |
| `org.reactivestreams:reactive-streams:1.0.3` | CC0-1.0 | commercial, no attribution required | http://creativecommons.org/publicdomain/zero/1.0/ | yes |
| `org.slf4j:slf4j-api:2.0.17` | MIT | commercial, attribution | https://www.slf4j.org/license.html | yes |

## Assets, other flavours, models, voices, datasets

| Component | Licence | Terms | Verified from | Shipped in APK |
|---|---|---|---|---|
| Knotwork (app sources, imported v0.12.1) | Apache-2.0 | commercial, attribution | LICENSE; https://github.com/alexeyw/knotwork | yes |
| Inter (res/font) | SIL OFL 1.1 | commercial, attribution; font may not be sold by itself | app/src/main/assets/license_inter_ofl.txt | yes |
| JetBrains Mono (res/font) | SIL OFL 1.1 | commercial, attribution; font may not be sold by itself | app/src/main/assets/license_jetbrainsmono_ofl.txt | yes |
| Universal Sentence Encoder (universal_sentence_encoder.tflite) | Apache-2.0 | commercial, attribution | NOTICE; https://www.kaggle.com/models/google/universal-sentence-encoder | yes |
| Firebase Crashlytics (full flavour only, BoM 34.19.0) | Apache-2.0 | commercial, attribution | https://github.com/firebase/firebase-android-sdk/blob/master/LICENSE | full APK only; not in foss |
| Drawflow 0.0.60 (pipeline-editor.html only) | MIT | commercial, attribution | https://github.com/jerosoler/Drawflow | no |
| Gemma 4 E4B LiteRT (litert-community/gemma-4-E4B-it-litert-lm) | Gemma Terms of Use | commercial with restrictions, gated, prohibited-use policy | https://ai.google.dev/gemma/terms and https://ai.google.dev/gemma/prohibited_use_policy | no (owner download) |
| Gemma 4 E2B LiteRT (litert-community/gemma-4-E2B-it-litert-lm) | Gemma Terms of Use | commercial with restrictions, gated, prohibited-use policy | https://ai.google.dev/gemma/terms and https://ai.google.dev/gemma/prohibited_use_policy | no (owner download) |
| EmbeddingGemma | not yet verified | do not ship until the model card is checked | spec section 0.3; no file in this repo | no |
| Piper voices | per voice, not yet chosen | no download link until each voice is checked | spec section 0.3 | no |
| Sudanese Whisper fine-tunes | unknown | evaluation only; never shipped or downloaded by the app | spec section 0.3 | no |
| arbml datasets | unknown | evaluation only; never shipped or downloaded by the app | spec section 0.3 | no |
| OpenDroid (yashab-cyber/opendroid @ c08175cb) | Apache-2.0 | adapted into the app. LLM providers, social manager, finance, food, transport, and the MCP server were not copied | application source under accessibility and hands | yes, as adapted source |
| ARTEMIS embedded Minitap mobile-use | not ported | check third_party/mobile_use before any copy | spec section 0.3 | no |
| VoiceStudio | AGPL (reference only) | never copied | spec section 0.3 | no |
| 30u30 and CC BY-NC-ND material | non-commercial | never copied | spec section 0.3 | no |

