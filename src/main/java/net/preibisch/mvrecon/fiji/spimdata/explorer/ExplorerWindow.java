/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2023 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BasicBDVPopup;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;

public interface ExplorerWindow< AS extends AbstractSpimData< ? > >
{
	public List< BasicViewDescription< ? > > selectedRows();
	public List< ViewId > selectedRowsViewId();
	public AS getSpimData();
	public void updateContent();
	public String xml();
	public void saveXML();

	public boolean tilesGrouped();
	public boolean illumsGrouped();
	public boolean channelsGrouped();

	// BDV-specific
	public BasicBDVPopup bdvPopup();
	public boolean colorMode();
	public BasicViewDescription< ? > firstSelectedVD();
}
