// Copyright (c) 2012, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

library html_common;

import 'dart:collection';
import 'dart:html';
import 'dart:typed_data';
import 'dart:_js_helper' show Creates, Returns;
import 'dart:_foreign_helper' show JS;
import 'dart:_interceptors' show Interceptor, JSExtendableArray;

import 'metadata.dart';
export 'metadata.dart';

part 'css_class_set.dart';
part 'conversions.dart';
part 'device.dart';
part 'filtered_element_list.dart';
part 'jenkins_smi_hash.dart';
part 'lists.dart';

// For annotating deprecated APIs.
// TODO: remove once @deprecated is added to dart core.
const deprecated = 0;
