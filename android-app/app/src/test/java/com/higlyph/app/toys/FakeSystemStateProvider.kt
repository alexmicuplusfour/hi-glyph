package com.higlyph.app.toys

class FakeSystemStateProvider : SystemStateProvider {
    override var isCallActive: Boolean = false
    override var isMediaPlaying: Boolean = false
}
