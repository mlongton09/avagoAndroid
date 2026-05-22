package com.avago.feature.workorders.nav

// This file is superseded by WorkOrderNavGraph.kt which contains the full
// Phase 7 nav graph implementation. Kept as an empty file to avoid breaking
// any existing import references in the app module.
//
// Use WorkOrderRoute and workOrderNavGraph() from WorkOrderNavGraph.kt instead.
//
// Log capture routing notes (iOS parity):
// When a WO is in_progress and a tech taps "Log Work", navigate to:
//   LogRoute.addEdit(assetId = assetId)
// using the assetId from the work order. The log feature nav graph handles
// pre-populating the form. See WorkOrderNavGraph.kt for the wired onLogWork callback.
