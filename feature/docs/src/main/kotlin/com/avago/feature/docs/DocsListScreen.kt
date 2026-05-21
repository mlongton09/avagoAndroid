// This file is intentionally left as a redirect.
// The real implementation is at com.avago.feature.docs.ui.DocListScreen.
// Kept to preserve any existing import references; delete once all call sites are updated.
package com.avago.feature.docs

@Deprecated(
    message = "Use com.avago.feature.docs.ui.DocListScreen instead.",
    replaceWith = ReplaceWith(
        expression = "DocListScreen(onDocClick, onAddDoc)",
        imports = ["com.avago.feature.docs.ui.DocListScreen"],
    ),
)
object DocsListScreenStub
