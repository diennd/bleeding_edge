// Copyright (c) 2013, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import 'dart:async';
import 'package:observe/observe.dart';
import 'package:unittest/unittest.dart';
import 'observe_test_utils.dart';

// This file contains code ported from:
// https://github.com/rafaelw/ChangeSummary/blob/master/tests/test.js

main() {
  group('PathObserver', observePathTests);
}

observePath(obj, path) => new PathObserver(obj, path);

sym(x) => new Symbol(x);

toSymbolMap(Map map) {
  var result = new ObservableMap.linked();
  map.forEach((key, value) {
    if (value is Map) value = toSymbolMap(value);
    result[new Symbol(key)] = value;
  });
  return result;
}

observePathTests() {
  observeTest('Degenerate Values', () {
    expect(observePath(null, '').value, null);
    expect(observePath(123, '').value, 123);
    expect(observePath(123, 'foo.bar.baz').value, null);

    // shouldn't throw:
    observePath(123, '').changes.listen((_) {}).cancel();
    observePath(null, '').value = null;
    observePath(123, '').value = 42;
    observePath(123, 'foo.bar.baz').value = 42;

    var foo = {};
    expect(observePath(foo, '').value, foo);

    foo = new Object();
    expect(observePath(foo, '').value, foo);

    expect(observePath(foo, 'a/3!').value, null);
  });

  observeTest('get value at path ObservableBox', () {
    var obj = new ObservableBox(new ObservableBox(new ObservableBox(1)));

    expect(observePath(obj, '').value, obj);
    expect(observePath(obj, 'value').value, obj.value);
    expect(observePath(obj, 'value.value').value, obj.value.value);
    expect(observePath(obj, 'value.value.value').value, 1);

    obj.value.value.value = 2;
    expect(observePath(obj, 'value.value.value').value, 2);

    obj.value.value = new ObservableBox(3);
    expect(observePath(obj, 'value.value.value').value, 3);

    obj.value = new ObservableBox(4);
    expect(observePath(obj, 'value.value.value').value, null);
    expect(observePath(obj, 'value.value').value, 4);
  });


  observeTest('get value at path ObservableMap', () {
    var obj = toSymbolMap({'a': {'b': {'c': 1}}});

    expect(observePath(obj, '').value, obj);
    expect(observePath(obj, 'a').value, obj[sym('a')]);
    expect(observePath(obj, 'a.b').value, obj[sym('a')][sym('b')]);
    expect(observePath(obj, 'a.b.c').value, 1);

    obj[sym('a')][sym('b')][sym('c')] = 2;
    expect(observePath(obj, 'a.b.c').value, 2);

    obj[sym('a')][sym('b')] = toSymbolMap({'c': 3});
    expect(observePath(obj, 'a.b.c').value, 3);

    obj[sym('a')] = toSymbolMap({'b': 4});
    expect(observePath(obj, 'a.b.c').value, null);
    expect(observePath(obj, 'a.b').value, 4);
  });

  observeTest('set value at path', () {
    var obj = toSymbolMap({});
    observePath(obj, 'foo').value = 3;
    expect(obj[sym('foo')], 3);

    var bar = toSymbolMap({ 'baz': 3 });
    observePath(obj, 'bar').value = bar;
    expect(obj[sym('bar')], bar);

    observePath(obj, 'bar.baz.bat').value = 'not here';
    expect(observePath(obj, 'bar.baz.bat').value, null);
  });

  observeTest('set value back to same', () {
    var obj = toSymbolMap({});
    var path = observePath(obj, 'foo');
    var values = [];
    path.changes.listen((_) { values.add(path.value); });

    path.value = 3;
    expect(obj[sym('foo')], 3);
    expect(path.value, 3);

    observePath(obj, 'foo').value = 2;
    performMicrotaskCheckpoint();
    expect(path.value, 2);
    expect(observePath(obj, 'foo').value, 2);

    observePath(obj, 'foo').value = 3;
    performMicrotaskCheckpoint();
    expect(path.value, 3);

    performMicrotaskCheckpoint();
    expect(values, [2, 3]);
  });

  observeTest('Observe and Unobserve - Paths', () {
    var arr = toSymbolMap({});

    arr[sym('foo')] = 'bar';
    var fooValues = [];
    var fooPath = observePath(arr, 'foo');
    var fooSub = fooPath.changes.listen((_) {
      fooValues.add(fooPath.value);
    });
    arr[sym('foo')] = 'baz';
    arr[sym('bat')] = 'bag';
    var batValues = [];
    var batPath = observePath(arr, 'bat');
    var batSub = batPath.changes.listen((_) {
      batValues.add(batPath.value);
    });

    performMicrotaskCheckpoint();
    expect(fooValues, ['baz']);
    expect(batValues, []);

    arr[sym('foo')] = 'bar';
    fooSub.cancel();
    arr[sym('bat')] = 'boo';
    batSub.cancel();
    arr[sym('bat')] = 'boot';

    performMicrotaskCheckpoint();
    expect(fooValues, ['baz']);
    expect(batValues, []);
  });

  observeTest('Path Value With Indices', () {
    var model = toObservable([]);
    var path = observePath(model, '0');
    path.changes.listen(expectAsync1((_) {
      expect(path.value, 123);
    }));
    model.add(123);
  });

  for (var createModel in [() => new TestModel(), () => new WatcherModel()]) {
    observeTest('Path Observation - ${createModel().runtimeType}', () {
      var model = createModel()..a =
          (createModel()..b = (createModel()..c = 'hello, world'));

      var path = observePath(model, 'a.b.c');
      var lastValue = null;
      var sub = path.changes.listen((_) { lastValue = path.value; });

      model.a.b.c = 'hello, mom';

      expect(lastValue, null);
      performMicrotaskCheckpoint();
      expect(lastValue, 'hello, mom');

      model.a.b = createModel()..c = 'hello, dad';
      performMicrotaskCheckpoint();
      expect(lastValue, 'hello, dad');

      model.a = createModel()..b =
          (createModel()..c = 'hello, you');
      performMicrotaskCheckpoint();
      expect(lastValue, 'hello, you');

      model.a.b = 1;
      performMicrotaskCheckpoint();
      expect(lastValue, null);

      // Stop observing
      sub.cancel();

      model.a.b = createModel()..c = 'hello, back again -- but not observing';
      performMicrotaskCheckpoint();
      expect(lastValue, null);

      // Resume observing
      sub = path.changes.listen((_) { lastValue = path.value; });

      model.a.b.c = 'hello. Back for reals';
      performMicrotaskCheckpoint();
      expect(lastValue, 'hello. Back for reals');
    });
  }

  observeTest('observe map', () {
    var model = toSymbolMap({'a': 1});
    var path = observePath(model, 'a');

    var values = [path.value];
    var sub = path.changes.listen((_) { values.add(path.value); });
    expect(values, [1]);

    model[sym('a')] = 2;
    performMicrotaskCheckpoint();
    expect(values, [1, 2]);

    sub.cancel();
    model[sym('a')] = 3;
    performMicrotaskCheckpoint();
    expect(values, [1, 2]);
  });
}

class TestModel extends ChangeNotifierBase {
  var _a, _b, _c;

  TestModel();

  get a => _a;

  void set a(newValue) {
    _a = notifyPropertyChange(const Symbol('a'), _a, newValue);
  }

  get b => _b;

  void set b(newValue) {
    _b = notifyPropertyChange(const Symbol('b'), _b, newValue);
  }

  get c => _c;

  void set c(newValue) {
    _c = notifyPropertyChange(const Symbol('c'), _c, newValue);
  }
}

class WatcherModel extends ObservableBase {
  // TODO(jmesserly): dart2js does not let these be on the same line:
  // @observable var a, b, c;
  @observable var a;
  @observable var b;
  @observable var c;

  WatcherModel();
}
