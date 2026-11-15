import React from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';

export const AppShell: React.FC = () => {
  return (
    <div className="flex h-screen w-screen overflow-hidden bg-[#D3B996]">
      <Sidebar />
      <main className="flex-1 h-screen overflow-y-auto px-10 py-8">
        <div className="max-w-6xl mx-auto">
          <Outlet />
        </div>
      </main>
    </div>
  );
};

