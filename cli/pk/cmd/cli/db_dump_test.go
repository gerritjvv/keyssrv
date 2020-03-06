// Copyright © 2018 PKHub <admin@pkhub.io>
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cli

import (
	"reflect"
	"sort"
	"testing"
)

func TestRemoveExcludes(t *testing.T) {

	tables := []string{"A", "B", "C", "D", "E"}
	exclude := []string{"b", "e"}

	tables2 := removeExcludes(tables, exclude)

	sort.Strings(tables2)

	expected := []string{"A", "C", "D"}

	if reflect.DeepEqual(tables2, expected) {
		t.Fail()
	}
}
