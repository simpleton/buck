/*
 * Copyright 2013-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.test.selectors;


import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A way of matching a test-method in a test-class, and saying whether or not to include any matches
 * in a test run.
 *
 * A {@link TestDescription} will match if this selector's class-part is a substring of the
 * {@link TestDescription}'s full class-name, or if this selector's class-name, when interpreted
 * as a java.util.regex regular-expression, matches the {@link TestDescription}'s full class-name.
 *
 * (The same rules apply for the method-name as well.  If this selector's class-part or method-part
 * are null, all class-names or method-names will match.)
 */
public class TestSelector {

  private boolean inclusive;
  /* @Nullable */ private Pattern classPattern;
  /* @Nullable */ private Pattern methodPattern;

  TestSelector(
      boolean inclusive,
      /* @Nullable */ Pattern classPattern,
      /* @Nullable */ Pattern methodPattern) {
    this.inclusive = inclusive;
    this.classPattern = classPattern;
    this.methodPattern = methodPattern;
  }

  /**
   * Build a {@link TestSelector} from the given String.  Selector strings should be of the form
   * "[is-exclusive][class-part]#[method-part]".  If "[is-exclusive]" is a "!" then this selector
   * will exclude tests, otherwise it will include tests.
   *
   * If the class-part (or method-part) are omitted, then all classes or methods will match.
   * Consequently "#" means "include everything" and "!#" means "exclude everything".
   *
   * If the selector string doesn't contain a "#" at all, it is interpreted as a class-part.
   *
   * @param rawSelectorString An unparsed selector string.
   */
  public static TestSelector buildFrom(String rawSelectorString) {
    if (rawSelectorString == null || rawSelectorString.isEmpty()) {
      throw new RuntimeException("Cannot build from a null or empty string!");
    }

    int hashCount = 0;
    for (int position = 0; position < rawSelectorString.length(); position++) {
      if (rawSelectorString.charAt(position) == '#') {
        hashCount++;
      }
    }
    if (hashCount > 1) {
      throw new TestSelectorParseException(String.format(
          "Test selector '%s' contains more than one '#'!",
          rawSelectorString));
    }

    boolean isInclusive = true;
    String remainder;
    if (rawSelectorString.charAt(0) == '!') {
      isInclusive = false;
      remainder = rawSelectorString.substring(1);
    } else {
      remainder = rawSelectorString;
    }

    Pattern classPattern = null;
    Pattern methodPattern = null;
    String[] parts = remainder.split("#");

    try {
      switch (parts.length) {
        // "#"
        case 0:
          break;

        // "com.example.Test", "com.example.Test#"
        case 1:
          classPattern = Pattern.compile(parts[0]);
          break;

        // "com.example.Test#testX", "#testX"
        case 2:
          classPattern = parts[0].isEmpty() ? null : Pattern.compile(parts[0]);
          methodPattern = Pattern.compile(parts[1]);
          break;
      }
    } catch (PatternSyntaxException e) {
      throw new TestSelectorParseException(String.format(
          "Regular expression error in '%s': %s",
          rawSelectorString,
          e.getMessage()));
    }

    return new TestSelector(isInclusive, classPattern, methodPattern);
  }

  String getExplanation() {
    return String.format("%s class:%s method:%s",
        isInclusive() ? "include" : "exclude",
        isMatchAnyClass() ? "<any>" : classPattern,
        isMatchAnyMethod() ? "<any>" : methodPattern);
  }

  boolean isInclusive() {
    return inclusive;
  }

  boolean isMatchAnyClass() {
    return classPattern == null;
  }

  boolean isMatchAnyMethod() {
    return methodPattern == null;
  }

  boolean matches(TestDescription description) {
    String aClassName = description.getClassName();
    String aMethodName = description.getMethodName();

    boolean isClassMatch = isMatchAnyClass() || classPattern.matcher(aClassName).find();
    boolean isMethodMatch = isMatchAnyMethod() || methodPattern.matcher(aMethodName).find();
    return isClassMatch && isMethodMatch;
  }
}
