package dev.tim1337.lumio.render;

/** Implemented (via mixin) by the item render state. */
public interface OutlineItem {
	int lumio$layerCount();

	OutlineLayer lumio$layer(int index);
}
